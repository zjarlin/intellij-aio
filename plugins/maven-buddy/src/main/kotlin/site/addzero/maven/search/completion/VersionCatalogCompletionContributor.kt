package site.addzero.maven.search.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.progress.ProgressManager
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import site.addzero.maven.search.cache.SearchResultCacheService
import site.addzero.maven.search.history.ArtifactHistoryEntry
import site.addzero.maven.search.history.SearchHistoryService
import site.addzero.maven.search.settings.MavenSearchSettings
import site.addzero.network.call.maven.util.MavenArtifact
import site.addzero.network.call.maven.util.MavenCentralPaginatedSearchUtil

/**
 * Gradle Version Catalog (libs.versions.toml) 依赖补全
 * 
 * 支持场景：
 * 1. [libraries] 部分的依赖声明
 * 2. 简写形式: guava = "com.google.guava:guava:  -> 补全版本
 * 3. 模块形式: { module = "com.google.guava:guava", version = "  -> 补全版本
 * 4. 完整形式: { group = "com.google.guava", name = "guava", version = "  -> 补全
 */
class VersionCatalogCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement()
                .inFile(PlatformPatterns.psiFile().withName(PlatformPatterns.string().matches(".*\\.versions\\.toml"))),
            VersionCatalogCompletionProvider()
        )
    }
}

private class VersionCatalogCompletionProvider : CompletionProvider<CompletionParameters>() {

    private val historyService by lazy { SearchHistoryService.getInstance() }
    private val cacheService by lazy { SearchResultCacheService.getInstance() }
    private val settings by lazy { MavenSearchSettings.getInstance() }

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val document = parameters.editor.document
        val offset = parameters.offset
        val text = document.text

        // 检测上下文
        val ctx = detectContext(text, offset) ?: return

        val query = ctx.query
        val prefixMatcher = result.withPrefixMatcher(query)

        // 优先显示历史记录
        if (historyService.enableHistory) {
            val historyArtifacts = when {
                query.length < 2 -> historyService.recentArtifacts(15)
                else -> historyService.matchArtifacts(query, 8)
            }
            historyArtifacts.forEachIndexed { index, entry ->
                prefixMatcher.addElement(
                    createHistoryElement(entry, ctx, priority = 1000.0 - index)
                )
            }
        }

        // 查询长度太短，只显示历史
        if (query.length < 2) {
            result.restartCompletionOnAnyPrefixChange()
            return
        }

        // 检查缓存
        cacheService[query]?.let { cached ->
            cached.take(20).forEachIndexed { index, artifact ->
                prefixMatcher.addElement(
                    createArtifactElement(artifact, ctx, priority = 500.0 - index, fromCache = true)
                )
            }
            result.restartCompletionOnAnyPrefixChange()
            return
        }

        // 搜索 Maven Central
        ProgressManager.checkCanceled()

        runCatching {
            val session = MavenCentralPaginatedSearchUtil.searchByKeywordPaginated(
                keyword = query,
                pageSize = settings.pageSize.coerceIn(10, 30)
            )
            val artifacts = session.loadNextPage().artifacts
            
            // 缓存结果
            if (artifacts.isNotEmpty()) {
                cacheService[query] = artifacts
            }
            
            artifacts.forEachIndexed { index, artifact ->
                ProgressManager.checkCanceled()
                prefixMatcher.addElement(
                    createArtifactElement(artifact, ctx, priority = 100.0 - index, fromCache = false)
                )
            }
        }

        result.restartCompletionOnAnyPrefixChange()
    }

    /**
     * 检测 TOML 上下文
     */
    private fun detectContext(text: String, offset: Int): TomlContext? {
        // 检查是否在 [libraries] 部分
        val beforeCursor = text.substring(0, offset)
        val lastLibrariesIndex = beforeCursor.lastIndexOf("[libraries]")
        val lastOtherSectionIndex = maxOf(
            beforeCursor.lastIndexOf("[versions]"),
            beforeCursor.lastIndexOf("[bundles]"),
            beforeCursor.lastIndexOf("[plugins]")
        )
        
        // 不在 [libraries] 部分
        if (lastLibrariesIndex < 0 || lastOtherSectionIndex > lastLibrariesIndex) {
            return null
        }

        val lineStart = text.lastIndexOf('\n', offset - 1) + 1
        val lineText = text.substring(lineStart, offset)

        // 模式1: 简写形式 - name = "groupId:artifactId:version"
        // 例如: guava = "com.google.guava:guava:
        val shortPattern = Regex("""^[\w-]+\s*=\s*"([^"]*?)$""")
        shortPattern.find(lineText)?.let { match ->
            val query = match.groupValues[1]
            return TomlContext(
                format = TomlFormat.SHORT,
                query = extractSearchQuery(query),
                fullInput = query,
                queryStartOffset = offset - query.length
            )
        }

        // 模式2: module 形式 - { module = "groupId:artifactId", version = "..." }
        // 例如: { module = "com.google.guava:guava
        val modulePattern = Regex("""module\s*=\s*"([^"]*?)$""")
        modulePattern.find(lineText)?.let { match ->
            val query = match.groupValues[1]
            return TomlContext(
                format = TomlFormat.MODULE,
                query = extractSearchQuery(query),
                fullInput = query,
                queryStartOffset = offset - query.length
            )
        }

        // 模式3: group 形式 - { group = "...", name = "..." }
        // 例如: { group = "com.google.guava
        val groupPattern = Regex("""group\s*=\s*"([^"]*?)$""")
        groupPattern.find(lineText)?.let { match ->
            val query = match.groupValues[1]
            return TomlContext(
                format = TomlFormat.GROUP,
                query = query,
                fullInput = query,
                queryStartOffset = offset - query.length
            )
        }

        // 模式4: name 形式（在 group 之后）
        val namePattern = Regex("""name\s*=\s*"([^"]*?)$""")
        namePattern.find(lineText)?.let { match ->
            val query = match.groupValues[1]
            // 尝试提取同行的 group
            val groupMatch = Regex("""group\s*=\s*"([^"]+)"""").find(lineText)
            val groupId = groupMatch?.groupValues?.get(1)
            
            return TomlContext(
                format = TomlFormat.NAME,
                query = query,
                fullInput = query,
                queryStartOffset = offset - query.length,
                groupId = groupId
            )
        }

        // 模式5: version 形式
        val versionPattern = Regex("""version\s*=\s*"([^"]*?)$""")
        versionPattern.find(lineText)?.let { match ->
            val query = match.groupValues[1]
            return TomlContext(
                format = TomlFormat.VERSION,
                query = query,
                fullInput = query,
                queryStartOffset = offset - query.length
            )
        }

        return null
    }

    /**
     * 从输入中提取搜索关键词
     * "com.google.guava:guava:32" -> "com.google.guava:guava" 或 "guava"
     */
    private fun extractSearchQuery(input: String): String {
        val parts = input.split(":")
        return when {
            parts.size >= 2 -> "${parts[0]}:${parts[1]}"
            else -> input
        }
    }

    private fun createHistoryElement(
        entry: ArtifactHistoryEntry,
        ctx: TomlContext,
        priority: Double
    ): LookupElement {
        val insertText = formatInsertText(entry.groupId, entry.artifactId, entry.version, ctx)
        val displayText = "${entry.groupId}:${entry.artifactId}:${entry.version}"

        return PrioritizedLookupElement.withPriority(
            LookupElementBuilder.create(insertText)
                .withPresentableText(entry.artifactId)
                .withTailText(" ${entry.version}", true)
                .withTypeText("📜 ${entry.groupId}", true)
                .withIcon(AllIcons.Nodes.Favorite)
                .withBoldness(true)
                .withInsertHandler(createInsertHandler(ctx, insertText, entry.groupId, entry.artifactId, entry.version)),
            priority
        )
    }

    private fun createArtifactElement(
        artifact: MavenArtifact,
        ctx: TomlContext,
        priority: Double,
        fromCache: Boolean
    ): LookupElement {
        val version = artifact.latestVersion.ifBlank { artifact.version }
        val insertText = formatInsertText(artifact.groupId, artifact.artifactId, version, ctx)
        val cacheIndicator = if (fromCache) "💾 " else ""

        return PrioritizedLookupElement.withPriority(
            LookupElementBuilder.create(insertText)
                .withPresentableText(artifact.artifactId)
                .withTailText(" $version", true)
                .withTypeText("$cacheIndicator${artifact.groupId}", true)
                .withIcon(AllIcons.Nodes.PpLib)
                .withInsertHandler(createInsertHandler(ctx, insertText, artifact.groupId, artifact.artifactId, version)),
            priority
        )
    }

    /**
     * 根据上下文格式化插入文本
     */
    private fun formatInsertText(groupId: String, artifactId: String, version: String, ctx: TomlContext): String {
        return when (ctx.format) {
            TomlFormat.SHORT -> "$groupId:$artifactId:$version"
            TomlFormat.MODULE -> "$groupId:$artifactId"
            TomlFormat.GROUP -> groupId
            TomlFormat.NAME -> artifactId
            TomlFormat.VERSION -> version
        }
    }

    private fun createInsertHandler(
        ctx: TomlContext,
        insertText: String,
        groupId: String,
        artifactId: String,
        version: String
    ): InsertHandler<LookupElement> = InsertHandler { insertCtx, _ ->
        val document = insertCtx.document
        val startOffset = ctx.queryStartOffset
        val endOffset = insertCtx.tailOffset

        // 检查后面是否已有闭合引号
        val afterText = document.text.substring(endOffset, minOf(endOffset + 5, document.textLength))
        val hasTrailingQuote = afterText.startsWith("\"")

        val finalText = if (hasTrailingQuote) insertText else "$insertText\""

        document.replaceString(startOffset, endOffset, finalText)
        insertCtx.editor.caretModel.moveToOffset(startOffset + finalText.length)
        insertCtx.commitDocument()

        // 记录历史
        SearchHistoryService.getInstance().record(groupId, artifactId, version)
    }
}

private data class TomlContext(
    val format: TomlFormat,
    val query: String,
    val fullInput: String,
    val queryStartOffset: Int,
    val groupId: String? = null
)

private enum class TomlFormat {
    SHORT,      // guava = "com.google.guava:guava:32.1.3-jre"
    MODULE,     // { module = "com.google.guava:guava", version = "32.1.3-jre" }
    GROUP,      // { group = "com.google.guava", name = "guava", version = "32.1.3-jre" }
    NAME,       // name = "guava" (在 group 之后)
    VERSION     // version = "32.1.3-jre"
}

package site.addzero.gradle.buddy.intentions.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import site.addzero.network.call.maven.util.MavenArtifact
import site.addzero.network.call.maven.util.MavenCentralPaginatedSearchUtil
import site.addzero.network.call.maven.util.MavenCentralSearchUtil

/**
 * Gradle Version Catalog (libs.versions.toml) 依赖补全
 *
 * 支持场景：
 * 1. [libraries] 部分的值补全（引号内输入 groupId:artifactId）
 * 2. [libraries] 部分的裸 alias 输入 -> 基于上下文同 group 条目智能推断完整声明
 *    例如: 已有 jimmer-sql-kotlin = { module = "org.babyfish.jimmer:jimmer-sql-kotlin", version.ref = "jimmer" }
 *    输入 jimmer-ksp -> 补全为 jimmer-ksp = { module = "org.babyfish.jimmer:jimmer-ksp", version.ref = "jimmer" }
 * 3. 简写/module/group/name/version 各种格式的值补全
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

    // 所有 maven-buddy-core 服务通过 MavenBuddyBridge 安全访问（compileOnly，运行时可能不存在）

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val document = parameters.editor.document
        val offset = parameters.offset
        val text = document.text

        val ctx = detectContext(text, offset) ?: return

        // === 裸 alias 模式：基于上下文同 group 推断 ===
        if (ctx.format == TomlFormat.BARE_ALIAS) {
            handleBareAliasCompletion(text, ctx, result)
            return
        }

        // === 值补全模式（引号内） ===
        val query = ctx.query
        val prefixMatcher = result.withPrefixMatcher(query)

        if (MavenBuddyBridge.historyEnabled) {
            val historyArtifacts = when {
                query.length < 2 -> MavenBuddyBridge.recentArtifacts(15)
                else -> MavenBuddyBridge.matchArtifacts(query, 8)
            }
            historyArtifacts.forEachIndexed { index, entry ->
                prefixMatcher.addElement(
                    createHistoryElement(entry, ctx, priority = 10000.0 - index)
                )
            }
        }

        if (query.length < 2) {
            result.restartCompletionOnAnyPrefixChange()
            return
        }

        @Suppress("UNCHECKED_CAST")
        val cached = (MavenBuddyBridge.cacheMatch(query, limit = 20) as? List<MavenArtifact>) ?: emptyList()
        if (cached.isNotEmpty()) {
            cached.forEachIndexed { index, artifact ->
                ProgressManager.checkCanceled()
                val resolvedVersion = resolveLatestVersion(artifact.groupId, artifact.artifactId, artifact.latestVersion.ifBlank { artifact.version })
                prefixMatcher.addElement(
                    createArtifactElement(artifact, resolvedVersion, ctx, priority = 5000.0 - index, fromCache = true)
                )
            }
            result.restartCompletionOnAnyPrefixChange()
            return
        }

        ProgressManager.checkCanceled()
        runCatching {
            val session = MavenCentralPaginatedSearchUtil.searchByKeywordPaginated(
                keyword = query,
                pageSize = MavenBuddyBridge.pageSize.coerceIn(10, 30)
            )
            val artifacts = session.loadNextPage().artifacts
            if (artifacts.isNotEmpty()) MavenBuddyBridge.cacheAddAll(artifacts)

            artifacts.forEachIndexed { index, artifact ->
                ProgressManager.checkCanceled()
                val resolvedVersion = resolveLatestVersion(artifact.groupId, artifact.artifactId, artifact.latestVersion.ifBlank { artifact.version })
                prefixMatcher.addElement(
                    createArtifactElement(artifact, resolvedVersion, ctx, priority = 1000.0 - index, fromCache = false)
                )
            }
        }
        result.restartCompletionOnAnyPrefixChange()
    }

    /**
     * 裸 alias 补全：在 [libraries] 下直接输入 alias 关键字
     *
     * 逻辑：
     * 1. 解析文件中所有已有的 library 条目
     * 2. 用输入的关键字搜索 Maven Central
     * 3. 对每个搜索结果，检查是否有同 group 的已有条目
     *    - 有 -> 复用其 version.ref，alias 用 group-artifact 原则
     *    - 没有 -> 生成新的 version.ref
     * 4. 生成完整的 alias = { module = "...", version.ref = "..." } 行
     */
    private fun handleBareAliasCompletion(
        text: String,
        ctx: TomlContext,
        result: CompletionResultSet
    ) {
        val query = ctx.query
        if (query.isBlank()) return

        val prefixMatcher = result.withPrefixMatcher(query)
        val existingLibs = parseExistingLibraries(text)

        // 先从历史记录中匹配
        if (MavenBuddyBridge.historyEnabled && query.length >= 2) {
            val historyArtifacts = MavenBuddyBridge.matchArtifacts(query, 5)
            historyArtifacts.forEachIndexed { index, entry ->
                val entryGroupId = MavenBuddyBridge.entryGroupId(entry)
                val entryArtifactId = MavenBuddyBridge.entryArtifactId(entry)
                val entryVersion = MavenBuddyBridge.entryVersion(entry)
                val suggestion = buildAliasSuggestion(entryGroupId, entryArtifactId, entryVersion, existingLibs)
                prefixMatcher.addElement(
                    PrioritizedLookupElement.withPriority(
                        LookupElementBuilder.create(suggestion.fullLine)
                            .withPresentableText(suggestion.alias)
                            .withTailText("  $entryGroupId:$entryArtifactId:$entryVersion", true)
                            .withTypeText("📜 version.ref=${suggestion.versionRef}", true)
                            .withIcon(AllIcons.Nodes.Favorite)
                            .withBoldness(true)
                            .withInsertHandler(createBareAliasInsertHandler(ctx, suggestion, entryGroupId, entryArtifactId, entryVersion)),
                        10000.0 - index
                    )
                )
            }
        }

        if (query.length < 2) {
            result.restartCompletionOnAnyPrefixChange()
            return
        }

        // 缓存
        @Suppress("UNCHECKED_CAST")
        val cached = (MavenBuddyBridge.cacheMatch(query, limit = 15) as? List<MavenArtifact>) ?: emptyList()
        if (cached.isNotEmpty()) {
            cached.forEachIndexed { index, artifact ->
                ProgressManager.checkCanceled()
                val version = resolveLatestVersion(artifact.groupId, artifact.artifactId, artifact.latestVersion.ifBlank { artifact.version })
                val suggestion = buildAliasSuggestion(artifact.groupId, artifact.artifactId, version, existingLibs)
                prefixMatcher.addElement(
                    PrioritizedLookupElement.withPriority(
                        LookupElementBuilder.create(suggestion.fullLine)
                            .withPresentableText(suggestion.alias)
                            .withTailText("  ${artifact.groupId}:${artifact.artifactId}:$version", true)
                            .withTypeText("💾 version.ref=${suggestion.versionRef}", true)
                            .withIcon(AllIcons.Nodes.PpLib)
                            .withInsertHandler(createBareAliasInsertHandler(ctx, suggestion, artifact.groupId, artifact.artifactId, version)),
                        5000.0 - index
                    )
                )
            }
            result.restartCompletionOnAnyPrefixChange()
            return
        }

        // Maven Central
        ProgressManager.checkCanceled()
        runCatching {
            val session = MavenCentralPaginatedSearchUtil.searchByKeywordPaginated(
                keyword = query,
                pageSize = MavenBuddyBridge.pageSize.coerceIn(10, 30)
            )
            val artifacts = session.loadNextPage().artifacts
            if (artifacts.isNotEmpty()) MavenBuddyBridge.cacheAddAll(artifacts)

            artifacts.forEachIndexed { index, artifact ->
                ProgressManager.checkCanceled()
                val version = resolveLatestVersion(artifact.groupId, artifact.artifactId, artifact.latestVersion.ifBlank { artifact.version })
                val suggestion = buildAliasSuggestion(artifact.groupId, artifact.artifactId, version, existingLibs)
                prefixMatcher.addElement(
                    PrioritizedLookupElement.withPriority(
                        LookupElementBuilder.create(suggestion.fullLine)
                            .withPresentableText(suggestion.alias)
                            .withTailText("  ${artifact.groupId}:${artifact.artifactId}:$version", true)
                            .withTypeText("version.ref=${suggestion.versionRef}", true)
                            .withIcon(AllIcons.Nodes.PpLib)
                            .withInsertHandler(createBareAliasInsertHandler(ctx, suggestion, artifact.groupId, artifact.artifactId, version)),
                        1000.0 - index
                    )
                )
            }
        }
        result.restartCompletionOnAnyPrefixChange()
    }

    /**
     * 基于同 group 已有条目构建 alias 建议
     *
     * alias 规则：groupId-artifactId（kebab-case），不拼 version
     *   - 先尝试纯 artifactId
     *   - 如果 alias 已存在且 groupId 不同 -> 加 groupId 前缀: groupId-artifactId
     * version.ref 规则：
     *   1. 如果同 group 已有条目有 version.ref -> 复用
     *   2. 否则 -> artifactId-kebab 作为 version key
     */
    private fun buildAliasSuggestion(
        groupId: String,
        artifactId: String,
        version: String,
        existingLibs: List<ParsedLibrary>
    ): AliasSuggestion {
        val artKebab = artifactId.replace(".", "-").replace("_", "-").lowercase()
        var alias = artKebab

        // 查找同 group 的已有条目
        val sameGroupLib = existingLibs.firstOrNull { it.groupId == groupId }
        val versionRef = sameGroupLib?.versionRef ?: artKebab

        // 如果 alias 已存在且属于不同 group -> 加 groupId 前缀
        val conflicting = existingLibs.firstOrNull { it.alias == alias }
        if (conflicting != null && conflicting.groupId != groupId) {
            val groupKebab = groupId.replace(".", "-").replace("_", "-").lowercase()
            alias = "$groupKebab-$artKebab"
        }

        val fullLine = "$alias = { module = \"$groupId:$artifactId\", version.ref = \"$versionRef\" }"
        return AliasSuggestion(alias, versionRef, fullLine, sameGroupLib != null)
    }

    /** 解析文件中 [libraries] 部分的所有已有条目 */
    private fun parseExistingLibraries(text: String): List<ParsedLibrary> {
        val result = mutableListOf<ParsedLibrary>()
        val lines = text.lines()
        var inLibraries = false

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed == "[libraries]" -> inLibraries = true
                trimmed.startsWith("[") && trimmed.endsWith("]") -> {
                    if (inLibraries) break  // 离开 [libraries]
                }
                inLibraries && trimmed.contains("=") -> {
                    val aliasMatch = Regex("""^([\w-]+)\s*=""").find(trimmed) ?: continue
                    val alias = aliasMatch.groupValues[1]

                    val moduleMatch = Regex("""module\s*=\s*"([^"]+)"""").find(trimmed)
                    val groupMatch = Regex("""group\s*=\s*"([^"]+)"""").find(trimmed)
                    val nameMatch = Regex("""name\s*=\s*"([^"]+)"""").find(trimmed)
                    val versionRefMatch = Regex("""version\.ref\s*=\s*"([^"]+)"""").find(trimmed)

                    val groupId = groupMatch?.groupValues?.get(1)
                        ?: moduleMatch?.groupValues?.get(1)?.substringBefore(":")
                        ?: continue
                    val artifactId = nameMatch?.groupValues?.get(1)
                        ?: moduleMatch?.groupValues?.get(1)?.substringAfter(":")
                        ?: continue

                    result.add(ParsedLibrary(
                        alias = alias,
                        groupId = groupId,
                        artifactId = artifactId,
                        versionRef = versionRefMatch?.groupValues?.get(1)
                    ))
                }
            }
        }
        return result
    }

    /** 检测 TOML 上下文 */
    private fun detectContext(text: String, offset: Int): TomlContext? {
        val beforeCursor = text.take(offset)
        val lastLibrariesIndex = beforeCursor.lastIndexOf("[libraries]")
        val lastOtherSectionIndex = maxOf(
            beforeCursor.lastIndexOf("[versions]"),
            beforeCursor.lastIndexOf("[bundles]"),
            beforeCursor.lastIndexOf("[plugins]")
        )

        if (lastLibrariesIndex < 0 || lastOtherSectionIndex > lastLibrariesIndex) return null

        val lineStart = text.lastIndexOf('\n', offset - 1) + 1
        val lineText = text.substring(lineStart, offset)

        // 模式1: 简写 name = "groupId:artifactId:version"
        Regex("""^[\w-]+\s*=\s*"([^"]*?)$""").find(lineText)?.let { match ->
            val query = match.groupValues[1]
            return TomlContext(TomlFormat.SHORT, extractSearchQuery(query), query, offset - query.length)
        }

        // 模式2: module = "groupId:artifactId"
        Regex("""module\s*=\s*"([^"]*?)$""").find(lineText)?.let { match ->
            val query = match.groupValues[1]
            return TomlContext(TomlFormat.MODULE, extractSearchQuery(query), query, offset - query.length)
        }

        // 模式3: group = "..."
        Regex("""group\s*=\s*"([^"]*?)$""").find(lineText)?.let { match ->
            val query = match.groupValues[1]
            return TomlContext(TomlFormat.GROUP, query, query, offset - query.length)
        }

        // 模式4: name = "..."
        Regex("""name\s*=\s*"([^"]*?)$""").find(lineText)?.let { match ->
            val query = match.groupValues[1]
            val groupId = Regex("""group\s*=\s*"([^"]+)"""").find(lineText)?.groupValues?.get(1)
            return TomlContext(TomlFormat.NAME, query, query, offset - query.length, groupId = groupId)
        }

        // 模式5: version = "..."
        Regex("""version\s*=\s*"([^"]*?)$""").find(lineText)?.let { match ->
            val query = match.groupValues[1]
            return TomlContext(TomlFormat.VERSION, query, query, offset - query.length)
        }

        // 模式6: 裸 alias 输入 — 行首只有 alias 关键字（不含 = 号）
        Regex("""^\s*([\w-]+)$""").find(lineText)?.let { match ->
            val query = match.groupValues[1]
            if (query.isBlank()) return@let
            return TomlContext(
                TomlFormat.BARE_ALIAS, query, query,
                lineStart + (lineText.length - lineText.trimStart().length)
            )
        }

        return null
    }

    private fun extractSearchQuery(input: String): String {
        val parts = input.split(":")
        return when {
            parts.size >= 2 -> "${parts[0]}:${parts[1]}"
            else -> input
        }
    }

    // === 值补全的 LookupElement 构建 ===

    private fun createHistoryElement(entry: Any, ctx: TomlContext, priority: Double): LookupElement {
        val entryGroupId = MavenBuddyBridge.entryGroupId(entry)
        val entryArtifactId = MavenBuddyBridge.entryArtifactId(entry)
        val entryVersion = MavenBuddyBridge.entryVersion(entry)
        val insertText = formatInsertText(entryGroupId, entryArtifactId, entryVersion, ctx)
        return PrioritizedLookupElement.withPriority(
            LookupElementBuilder.create(insertText)
                .withPresentableText(entryArtifactId)
                .withTailText(" $entryVersion", true)
                .withTypeText("📜 $entryGroupId", true)
                .withIcon(AllIcons.Nodes.Favorite)
                .withBoldness(true)
                .withInsertHandler(createValueInsertHandler(ctx, insertText, entryGroupId, entryArtifactId, entryVersion)),
            priority
        )
    }

    private fun createArtifactElement(artifact: MavenArtifact, resolvedVersion: String, ctx: TomlContext, priority: Double, fromCache: Boolean): LookupElement {
        val insertText = formatInsertText(artifact.groupId, artifact.artifactId, resolvedVersion, ctx)
        val cacheIndicator = if (fromCache) "💾 " else ""
        return PrioritizedLookupElement.withPriority(
            LookupElementBuilder.create(insertText)
                .withPresentableText(artifact.artifactId)
                .withTailText(" $resolvedVersion", true)
                .withTypeText("$cacheIndicator${artifact.groupId}", true)
                .withIcon(AllIcons.Nodes.PpLib)
                .withInsertHandler(createValueInsertHandler(ctx, insertText, artifact.groupId, artifact.artifactId, resolvedVersion)),
            priority
        )
    }

    private fun formatInsertText(groupId: String, artifactId: String, version: String, ctx: TomlContext): String {
        return when (ctx.format) {
            TomlFormat.SHORT -> "$groupId:$artifactId:$version"
            TomlFormat.MODULE -> "$groupId:$artifactId"
            TomlFormat.GROUP -> groupId
            TomlFormat.NAME -> artifactId
            TomlFormat.VERSION -> version
            TomlFormat.BARE_ALIAS -> "" // 不会走到这里
        }
    }

    /**
     * 解析真正的最新版本号。
     * 调用 MavenCentralSearchUtil.getLatestVersion()，保证返回版本 >= searchVersion（不降级）。
     */
    private fun resolveLatestVersion(groupId: String, artifactId: String, searchVersion: String): String {
        val resolved = runCatching {
            MavenCentralSearchUtil.getLatestVersion(groupId, artifactId)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return searchVersion
        return if (compareVersions(resolved, searchVersion) >= 0) resolved else searchVersion
    }

    /** 简单的版本比较：按 . 和 - 分段逐段比较数字，保证不降级 */
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(Regex("[.\\-]"))
        val parts2 = v2.split(Regex("[.\\-]"))
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrNull(i) ?: "0"
            val p2 = parts2.getOrNull(i) ?: "0"
            val n1 = p1.toLongOrNull()
            val n2 = p2.toLongOrNull()
            val cmp = if (n1 != null && n2 != null) n1.compareTo(n2) else p1.compareTo(p2)
            if (cmp != 0) return cmp
        }
        return 0
    }

    /** 值补全的 InsertHandler（引号内替换） — 版本已在补全阶段解析为最新 */
    private fun createValueInsertHandler(
        ctx: TomlContext, insertText: String,
        groupId: String, artifactId: String, version: String
    ): InsertHandler<LookupElement> = InsertHandler { insertCtx, _ ->
        val document = insertCtx.document
        val editor = insertCtx.editor
        val startOffset = ctx.queryStartOffset
        val endOffset = insertCtx.tailOffset
        val afterText = document.text.substring(endOffset, minOf(endOffset + 5, document.textLength))
        val hasTrailingQuote = afterText.startsWith("\"")
        val finalText = if (hasTrailingQuote) insertText else "$insertText\""
        document.replaceString(startOffset, endOffset, finalText)
        editor.caretModel.moveToOffset(startOffset + finalText.length)
        insertCtx.commitDocument()

        // 记录历史
        ApplicationManager.getApplication().executeOnPooledThread {
            MavenBuddyBridge.recordHistory(groupId, artifactId, version)
        }
    }

    /** 裸 alias 补全的 InsertHandler（替换整行） — 版本已在补全阶段解析为最新 */
    private fun createBareAliasInsertHandler(
        ctx: TomlContext, suggestion: AliasSuggestion,
        groupId: String, artifactId: String, version: String
    ): InsertHandler<LookupElement> = InsertHandler { insertCtx, _ ->
        val document = insertCtx.document
        val editor = insertCtx.editor
        val project = editor.project
        val startOffset = ctx.queryStartOffset
        val endOffset = insertCtx.tailOffset
        document.replaceString(startOffset, endOffset, suggestion.fullLine)
        editor.caretModel.moveToOffset(startOffset + suggestion.fullLine.length)
        insertCtx.commitDocument()

        // 如果没有复用已有 version.ref，需要在 [versions] 中添加
        if (!suggestion.reusedVersionRef) {
            ApplicationManager.getApplication().executeOnPooledThread {
                ApplicationManager.getApplication().invokeLater {
                    WriteCommandAction.runWriteCommandAction(project) {
                        val text = document.text
                        val versionEntry = "${suggestion.versionRef} = \"$version\""
                        val versionsIdx = text.indexOf("[versions]")
                        if (versionsIdx >= 0) {
                            val versionKeyRegex = Regex("""^\s*${Regex.escape(suggestion.versionRef)}\s*=""", RegexOption.MULTILINE)
                            if (!versionKeyRegex.containsMatchIn(text)) {
                                val afterVersions = text.substring(versionsIdx + "[versions]".length)
                                val nextSection = Regex("""\n\[""").find(afterVersions)
                                val insertAt = if (nextSection != null) {
                                    versionsIdx + "[versions]".length + nextSection.range.first
                                } else {
                                    text.length
                                }
                                document.insertString(insertAt, "\n$versionEntry")
                            }
                        }
                    }
                }
                MavenBuddyBridge.recordHistory(groupId, artifactId, version)
            }
        } else {
            ApplicationManager.getApplication().executeOnPooledThread {
                MavenBuddyBridge.recordHistory(groupId, artifactId, version)
            }
        }
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
    SHORT,       // guava = "com.google.guava:guava:32.1.3-jre"
    MODULE,      // { module = "com.google.guava:guava", version = "..." }
    GROUP,       // { group = "com.google.guava", ... }
    NAME,        // name = "guava"
    VERSION,     // version = "32.1.3-jre"
    BARE_ALIAS   // 裸 alias 输入：jimmer-ksp (行首，不含 =)
}

private data class ParsedLibrary(
    val alias: String,
    val groupId: String,
    val artifactId: String,
    val versionRef: String?
)

private data class AliasSuggestion(
    val alias: String,
    val versionRef: String,
    val fullLine: String,
    val reusedVersionRef: Boolean
)

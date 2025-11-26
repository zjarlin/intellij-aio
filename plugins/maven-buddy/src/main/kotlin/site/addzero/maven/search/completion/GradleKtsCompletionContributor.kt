package site.addzero.maven.search.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.progress.ProgressManager
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import site.addzero.maven.search.history.ArtifactHistoryEntry
import site.addzero.maven.search.history.SearchHistoryService
import site.addzero.maven.search.settings.MavenSearchSettings
import site.addzero.network.call.maven.util.MavenArtifact
import site.addzero.network.call.maven.util.MavenCentralPaginatedSearchUtil

/**
 * Gradle Kotlin Script 依赖补全
 * 
 * 支持场景：
 * 1. implementation("tool-cur   -> 在引号内输入时补全
 * 2. implementation(tool-cur    -> 括号内无引号时输入补全（自动加引号）
 * 3. 空输入时显示历史记录
 */
class GradleKtsCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement()
                .inFile(PlatformPatterns.psiFile().withName(PlatformPatterns.string().endsWith(".gradle.kts"))),
            GradleDependencyCompletionProvider()
        )
    }
}

private class GradleDependencyCompletionProvider : CompletionProvider<CompletionParameters>() {
    
    private val historyService by lazy { SearchHistoryService.getInstance() }
    private val settings by lazy { MavenSearchSettings.getInstance() }
    
    companion object {
        private val DEPENDENCY_METHODS = setOf(
            "implementation", "api", "compileOnly", "runtimeOnly",
            "testImplementation", "testCompileOnly", "testRuntimeOnly",
            "kapt", "ksp", "annotationProcessor", "classpath"
        )
    }

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val document = parameters.editor.document
        val offset = parameters.offset
        val text = document.text

        // 检查是否在依赖声明上下文
        val ctx = detectContext(text, offset) ?: return
        
        val query = ctx.query
        val prefixMatcher = result.withPrefixMatcher(query)

        // 优先显示历史记录
        if (historyService.enableHistory) {
            val historyArtifacts = when {
                query.length < 2 -> historyService.recentArtifacts(10)
                else -> historyService.matchArtifacts(query, 5)
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

        // 搜索 Maven Central
        ProgressManager.checkCanceled()
        
        runCatching {
            val session = MavenCentralPaginatedSearchUtil.searchByKeywordPaginated(
                keyword = query,
                pageSize = settings.pageSize.coerceIn(5, 30)
            )
            session.loadNextPage().artifacts.forEachIndexed { index, artifact ->
                ProgressManager.checkCanceled()
                prefixMatcher.addElement(
                    createArtifactElement(artifact, ctx, priority = 100.0 - index)
                )
            }
        }

        result.restartCompletionOnAnyPrefixChange()
    }

    /**
     * 检测依赖上下文
     * 
     * 支持模式：
     * - implementation("com.google  -> hasOpenQuote=true, hasCloseQuote=false
     * - implementation("com.google" -> hasOpenQuote=true, hasCloseQuote=true
     * - implementation(com.google   -> hasOpenQuote=false
     */
    private fun detectContext(text: String, offset: Int): DependencyContext? {
        val lineStart = text.lastIndexOf('\n', offset - 1) + 1
        val lineText = text.substring(lineStart, offset)
        
        // 模式1: implementation("xxx 或 implementation("xxx"
        val quotedPattern = Regex("""(\w+)\s*\(\s*"([^"]*)(")?$""")
        quotedPattern.find(lineText)?.let { match ->
            val method = match.groupValues[1]
            if (method !in DEPENDENCY_METHODS) return null
            
            val query = match.groupValues[2]
            val hasCloseQuote = match.groupValues[3].isNotEmpty()
            
            return DependencyContext(
                methodName = method,
                hasOpenQuote = true,
                hasCloseQuote = hasCloseQuote,
                query = query,
                queryStartOffset = offset - query.length - (if (hasCloseQuote) 1 else 0)
            )
        }
        
        // 模式2: implementation(xxx （无引号）
        val unquotedPattern = Regex("""(\w+)\s*\(\s*([^"()\s]*)$""")
        unquotedPattern.find(lineText)?.let { match ->
            val method = match.groupValues[1]
            if (method !in DEPENDENCY_METHODS) return null
            
            val query = match.groupValues[2]
            
            return DependencyContext(
                methodName = method,
                hasOpenQuote = false,
                hasCloseQuote = false,
                query = query,
                queryStartOffset = offset - query.length
            )
        }
        
        return null
    }

    private fun createHistoryElement(
        entry: ArtifactHistoryEntry,
        ctx: DependencyContext,
        priority: Double
    ): LookupElement {
        val coordinate = entry.coordinate
        
        return PrioritizedLookupElement.withPriority(
            LookupElementBuilder.create(coordinate)
                .withPresentableText(entry.artifactId)
                .withTailText(" ${entry.version}", true)
                .withTypeText("${entry.groupId} [recent]", true)
                .withIcon(AllIcons.Nodes.Favorite)
                .withBoldness(true)
                .withInsertHandler(createInsertHandler(ctx, coordinate)),
            priority
        )
    }

    private fun createArtifactElement(
        artifact: MavenArtifact,
        ctx: DependencyContext,
        priority: Double
    ): LookupElement {
        val version = artifact.latestVersion.ifBlank { artifact.version }
        val coordinate = "${artifact.groupId}:${artifact.artifactId}:$version"
        
        return PrioritizedLookupElement.withPriority(
            LookupElementBuilder.create(coordinate)
                .withPresentableText(artifact.artifactId)
                .withTailText(" $version", true)
                .withTypeText(artifact.groupId, true)
                .withIcon(AllIcons.Nodes.PpLib)
                .withInsertHandler(createInsertHandler(ctx, coordinate)),
            priority
        )
    }

    private fun createInsertHandler(
        ctx: DependencyContext,
        coordinate: String
    ): InsertHandler<LookupElement> = InsertHandler { insertCtx, _ ->
        val document = insertCtx.document
        val startOffset = ctx.queryStartOffset
        val endOffset = insertCtx.tailOffset

        // 检查后面是否已有闭合引号和括号
        val afterText = document.text.substring(endOffset, minOf(endOffset + 10, document.textLength))
        val hasTrailingQuote = afterText.startsWith("\"")
        val hasTrailingParen = afterText.startsWith("\")") || afterText.startsWith("\")")
        
        val insertText = buildString {
            if (!ctx.hasOpenQuote) append("\"")
            append(coordinate)
            when {
                ctx.hasCloseQuote || hasTrailingQuote -> { /* 已有闭合引号 */ }
                else -> append("\"")
            }
            if (!ctx.hasOpenQuote && !hasTrailingParen) append(")")
        }

        document.replaceString(startOffset, endOffset, insertText)
        insertCtx.editor.caretModel.moveToOffset(startOffset + insertText.length)
        insertCtx.commitDocument()
        
        // 记录历史
        coordinate.split(":").takeIf { it.size >= 2 }?.let { parts ->
            SearchHistoryService.getInstance().record(
                parts[0], parts[1], parts.getOrElse(2) { "" }
            )
        }
    }
}

private data class DependencyContext(
    val methodName: String,
    val hasOpenQuote: Boolean,
    val hasCloseQuote: Boolean,
    val query: String,
    val queryStartOffset: Int
)

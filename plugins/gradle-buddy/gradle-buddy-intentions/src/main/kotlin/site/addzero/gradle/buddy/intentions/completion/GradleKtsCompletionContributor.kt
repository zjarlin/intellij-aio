package site.addzero.gradle.buddy.intentions.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

/**
 * Gradle Kotlin Script 依赖补全
 *
 * 支持场景：
 * 1. implementation("tool-cur   -> 在引号内输入时补全
 * 2. implementation(tool-cur    -> 括号内无引号时输入补全（自动加引号）
 * 3. 空输入时显示推荐依赖列表
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
        val project = parameters.originalFile.project

        // 检测是否在依赖声明上下文
        val ctx = detectContext(text, offset) ?: return

        val query = ctx.query
        val prefixMatcher = result.withPrefixMatcher(query)

        // 优先显示推荐依赖列表（最高优先级）
        val suggestionService = GradleDependencySuggestionService.getInstance(project)
        val suggestions = suggestionService.getAllSuggestions()
        suggestions.forEachIndexed { index, suggestion ->
            // 只显示以查询开头的依赖
            if (suggestion.startsWith(query)) {
                prefixMatcher.addElement(
                    createSuggestionElement(suggestion, ctx, priority = 10000.0 - index)
                )
            }
        }

        // 查询长度太短，只显示推荐
        if (query.length < 2) {
            result.restartCompletionOnAnyPrefixChange()
            return
        }

        // 搜索 Maven Central（较低优先级）
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "正在搜索依赖...", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                runCatching {
                    // 搜索 artifactId
                    val artifacts = searchMavenCentral(query, indicator, project)
                    artifacts.forEachIndexed { index, artifact ->
                        ProgressManager.checkCanceled()
                        prefixMatcher.addElement(
                            createArtifactElement(artifact, ctx, 5000.0 - index)
                        )
                    }
                }
            }
        })

        result.restartCompletionOnAnyPrefixChange()
    }

    /**
     * 搜索 Maven Central 获取依赖建议
     */
    private fun searchMavenCentral(query: String, indicator: ProgressIndicator, project: Project): List<MavenArtifact> {
        // 简化处理，返回一些热门的匹配项
        val suggestionService = GradleDependencySuggestionService.getInstance(project)
        val allSuggestions = suggestionService.getAllSuggestions()
        return allSuggestions
            .filter { it.contains(query) }
            .take(10)
            .map { parseCoordinate(it) }
            .filterNotNull()
    }

    /**
     * 解析坐标字符串
     */
    private fun parseCoordinate(coordinate: String): MavenArtifact? {
        val parts = coordinate.split(":")
        return if (parts.size >= 2) {
            MavenArtifact(
                groupId = parts[0],
                artifactId = parts[1],
                version = if (parts.size >= 3) parts[2] else ""
            )
        } else {
            null
        }
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
        val unquotedPattern = Regex("""(\w+)\s*\(\s*([^")\s]*)$""")
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

    private fun createSuggestionElement(
        suggestion: String,
        ctx: DependencyContext,
        priority: Double
    ): LookupElement {
        val parts = suggestion.split(":")
        val groupId = parts.getOrNull(0) ?: ""
        val artifactId = parts.getOrNull(1) ?: ""
        val version = parts.getOrNull(2) ?: ""

        val displayText = if (version.isNotEmpty()) {
            "$groupId:$artifactId:$version"
        } else {
            "$groupId:$artifactId"
        }

        return PrioritizedLookupElement.withPriority(
            LookupElementBuilder.create(displayText)
                .withPresentableText(artifactId)
                .withTailText(if (version.isNotEmpty()) " :$version" else "", true)
                .withTypeText(groupId, true)
                .withIcon(AllIcons.Nodes.PpLib)
                .withBoldness(true)
                .withInsertHandler(createInsertHandler(ctx, suggestion)),
            priority
        )
    }

    private fun createArtifactElement(
        artifact: MavenArtifact,
        ctx: DependencyContext,
        priority: Double
    ): LookupElement {
        val version = artifact.version.ifBlank { "latest" }
        val coordinate = "${artifact.groupId}:${artifact.artifactId}:$version"

        return PrioritizedLookupElement.withPriority(
            LookupElementBuilder.create(coordinate)
                .withPresentableText(artifact.artifactId)
                .withTailText(" :$version", true)
                .withTypeText(artifact.groupId, true)
                .withIcon(AllIcons.Nodes.PpLib)
                .withBoldness(true)
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

        // 检查后面是否已有闭合引号、冒号和右括号
        val afterText = document.getText(TextRange(endOffset, minOf(endOffset + 20, document.textLength)))

        // 检测已有的闭合符号
        var hasClosingQuote = false
        var hasColon = false
        var hasClosingParen = false

        // 逐个字符检查
        for (char in afterText) {
            when (char) {
                '"' -> {
                    hasClosingQuote = true
                    hasClosingParen = afterText.substring(afterText.indexOf(char)).trimStart().contains(")")
                }
                ':' -> hasColon = true
                ')' -> hasClosingParen = true
                ' ', '\t', '\n', '\r' -> break
            }
        }

        // 构建插入文本
        val insertText = buildString {
            if (!ctx.hasOpenQuote) append("\"")
            append(coordinate)
            when {
                ctx.hasCloseQuote || hasClosingQuote || hasColon -> {
                    // 已有引号、冒号或括号，不需要添加任何闭合符号
                }
                hasClosingParen -> {
                    // 已有右括号，只添加引号
                    append("\"")
                }
                else -> {
                    // 没有任何闭合符号，添加引号和右括号
                    append("\")")
                }
            }
        }

        // 替换文本
        document.replaceString(startOffset, endOffset, insertText)
        insertCtx.editor.caretModel.moveToOffset(startOffset + insertText.length)
        insertCtx.commitDocument()
    }
}

private data class MavenArtifact(
    val groupId: String,
    val artifactId: String,
    val version: String
)

private data class DependencyContext(
    val methodName: String,
    val hasOpenQuote: Boolean,
    val hasCloseQuote: Boolean,
    val query: String,
    val queryStartOffset: Int
)

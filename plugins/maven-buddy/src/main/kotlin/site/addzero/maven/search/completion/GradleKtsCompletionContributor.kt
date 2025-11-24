//package site.addzero.maven.search.completion
//
//import com.intellij.codeInsight.completion.*
//import com.intellij.codeInsight.lookup.LookupElementBuilder
//import com.intellij.openapi.diagnostic.Logger
//import com.intellij.openapi.progress.ProgressManager
//import com.intellij.openapi.util.TextRange
//import com.intellij.patterns.PlatformPatterns
//import com.intellij.psi.PsiElement
//import com.intellij.psi.PsiLiteralExpression
//import com.intellij.psi.util.parents
//import com.intellij.util.ProcessingContext
//import org.jetbrains.kotlin.psi.KtStringTemplateExpression
//import site.addzero.maven.search.settings.MavenSearchSettings
//import site.addzero.network.call.maven.util.MavenArtifact
//import site.addzero.network.call.maven.util.MavenCentralSearchUtil
//
///**
// * Provides completion suggestions for Maven dependencies in Gradle Kotlin script files (*.kts).
// */
//class GradleKtsCompletionContributor : CompletionContributor() {
//
//    init {
//        val provider = MavenDependencyCompletionProvider()
//        extend(
//            CompletionType.BASIC,
//            PlatformPatterns.psiElement()
//                .inFile(PlatformPatterns.psiFile().withName(PlatformPatterns.string().endsWith(".kts"))),
//            provider
//        )
//    }
//
//    private class MavenDependencyCompletionProvider : CompletionProvider<CompletionParameters>() {
//        override fun addCompletions(
//            parameters: CompletionParameters,
//            context: ProcessingContext,
//            result: CompletionResultSet
//        ) {
//            val literalInfo = findLiteralInfo(parameters.position, parameters) ?: return
//
//            val rawPrefix = literalInfo.valueBeforeCaret
//            val query = rawPrefix.trim()
//
//            // 即使查询长度小于最小长度，也提供基本的补全建议
//            ProgressManager.checkCanceled()
//            val artifacts = fetchArtifacts(query)
//
//            val resultSet = result.withPrefixMatcher(rawPrefix)
//            if (artifacts.isEmpty()) {
//                // 如果没有找到匹配项，仍然提供一些通用的建议
//                addDefaultSuggestions(resultSet, query)
//                result.restartCompletionOnAnyPrefixChange()
//                return
//            }
//
//            for (artifact in artifacts) {
//                ProgressManager.checkCanceled()
//                val dependencyCoordinate = buildCoordinate(artifact)
//                val tailText = artifact.latestVersion.takeUnless { it.isBlank() } ?: artifact.version
//                val repository = artifact.repositoryId.takeUnless { it.isBlank() } ?: artifact.packaging
//                var lookup = LookupElementBuilder.create(dependencyCoordinate)
//                    .withPresentableText("${artifact.groupId}:${artifact.artifactId}")
//                    .withTailText(if (tailText.isBlank()) "" else ":$tailText", true)
//                    .withTypeText(repository, true)
//                    .withIcon(com.intellij.icons.AllIcons.Nodes.Artifact) // 添加图标使建议更明显
//                    .withInsertHandler { insertionContext, _ ->
//                        // 自动添加引号（如果需要）
//                        insertionContext.commitDocument()
//                    }
//                lookup = lookup
//                    .withLookupString(artifact.groupId)
//                    .withLookupString("${artifact.groupId}:${artifact.artifactId}")
//                    .withLookupString(dependencyCoordinate)
//                resultSet.addElement(PrioritizedLookupElement.withPriority(lookup, 10.0))
//            }
//
//            // 添加默认建议作为备选
//            addDefaultSuggestions(resultSet, query)
//            result.restartCompletionOnAnyPrefixChange()
//        }
//
//        private fun addDefaultSuggestions(resultSet: CompletionResultSet, query: String) {
//            // 添加一个提示元素，告诉用户如何使用搜索
//            val helpLookup = LookupElementBuilder.create("Search Maven Central for '$query'...")
//                .withTypeText("Press Enter to search", true)
//                .withIcon(com.intellij.icons.AllIcons.Actions.Find)
//                .withInsertHandler { context, _ ->
//                    // 当用户选择此选项时，打开搜索对话框或执行搜索
//                    context.editor.project?.let { project ->
//                        // 这里可以触发一个更全面的搜索
//                    }
//                }
//            resultSet.addElement(PrioritizedLookupElement.withPriority(helpLookup, 1.0))
//        }
//
//        private fun findLiteralInfo(position: PsiElement, parameters: CompletionParameters): LiteralInfo? {
//            // Check if we're in a string literal context
//            val parent = position.parent
//            if (parent !is PsiLiteralExpression && parent !is KtStringTemplateExpression) {
//                // 检查是否在方法调用参数中
//                val callExpression = position.parents(true)
//                    .firstOrNull { it is PsiLiteralExpression || it is KtStringTemplateExpression }
//                if (callExpression != null) {
//                    return processStringLiteral(callExpression, parameters)
//                }
//                return null
//            }
//
//            return processStringLiteral(parent, parameters)
//        }
//
//        private fun processStringLiteral(literal: PsiElement, parameters: CompletionParameters): LiteralInfo? {
//            if (literal is KtStringTemplateExpression && literal.hasInterpolation()) {
//                return null
//            }
//
//            val textRange = literal.textRange ?: return null
//            val document = parameters.editor.document
//            val literalText = document.getText(textRange)
//
//            val prefixLength = when {
//                literalText.startsWith("\"\"\"") -> 3
//                literalText.startsWith("\"") -> 1
//                else -> return null
//            }
//
//            val contentStart = textRange.startOffset + prefixLength
//            val caretOffset = parameters.offset.coerceAtLeast(contentStart)
//            if (caretOffset < contentStart) return null
//
//            val valueBeforeCaret = document.getText(TextRange(contentStart, caretOffset))
//            return LiteralInfo(valueBeforeCaret)
//        }
//
//        private fun buildCoordinate(artifact: MavenArtifact): String {
//            val version = artifact.latestVersion.takeUnless { it.isBlank() } ?: artifact.version
//            return if (version.isBlank()) {
//                "${artifact.groupId}:${artifact.artifactId}"
//            } else {
//                "${artifact.groupId}:${artifact.artifactId}:$version"
//            }
//        }
//
//        private fun fetchArtifacts(query: String): List<MavenArtifact> {
//            val now = System.currentTimeMillis()
//            val cached = lastCache
//            if (cached != null && cached.query == query && now - cached.timestamp <= CACHE_TTL_MS) {
//                return cached.artifacts
//            }
//
//            return try {
//                val limit = MavenSearchSettings.getInstance().maxResults.coerceIn(5, 50)
//                // 即使查询为空，也返回一些热门依赖
//                val searchTerm = if (query.isBlank()) "common" else query
//                val artifacts = MavenCentralSearchUtil.searchByKeyword(searchTerm, limit)
//                lastCache = CacheEntry(query, now, artifacts)
//                artifacts
//            } catch (t: Throwable) {
//                Logger.getInstance(GradleKtsCompletionContributor::class.java).warn("Maven completion query failed for '$query'", t)
//                emptyList()
//            }
//        }
//    }
//
//    private data class LiteralInfo(
//        val valueBeforeCaret: String
//    )
//
//    data class CacheEntry(
//        val query: String,
//        val timestamp: Long,
//        val artifacts: List<MavenArtifact>
//    )
//
//    companion object {
//        private const val MIN_QUERY_LENGTH = 1  // 降低最小查询长度
//        private const val CACHE_TTL_MS = 2_000L
//    }
//}
//
//@Volatile
//private var lastCache: GradleKtsCompletionContributor.CacheEntry? = null

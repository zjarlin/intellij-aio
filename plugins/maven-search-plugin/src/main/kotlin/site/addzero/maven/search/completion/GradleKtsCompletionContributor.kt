package site.addzero.maven.search.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.util.parents
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import site.addzero.maven.search.settings.MavenSearchSettings
import site.addzero.network.call.maven.util.MavenArtifact
import site.addzero.network.call.maven.util.MavenCentralSearchUtil

/**
 * Provides completion suggestions for Maven dependencies in Gradle Kotlin script files (*.kts).
 */
class GradleKtsCompletionContributor : CompletionContributor() {

    init {
        val provider = MavenDependencyCompletionProvider()
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement()
                .inFile(PlatformPatterns.psiFile().withName(PlatformPatterns.string().endsWith(".kts")))
                .withSuperParent(2, PsiLiteralExpression::class.java),
            provider
        )
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement()
                .inFile(PlatformPatterns.psiFile().withName(PlatformPatterns.string().endsWith(".kts")))
                .withSuperParent(2, KtStringTemplateExpression::class.java),
            provider
        )
    }

    private class MavenDependencyCompletionProvider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet
        ) {
            val literalInfo = findLiteralInfo(parameters.position, parameters) ?: return

            val rawPrefix = literalInfo.valueBeforeCaret
            val query = rawPrefix.trim()
            if (query.length < MIN_QUERY_LENGTH) {
                result.restartCompletionOnAnyPrefixChange()
                return
            }

            ProgressManager.checkCanceled()
            val artifacts = fetchArtifacts(query)
            if (artifacts.isEmpty()) {
                result.restartCompletionOnAnyPrefixChange()
                return
            }

            val resultSet = result.withPrefixMatcher(rawPrefix)
            for (artifact in artifacts) {
                ProgressManager.checkCanceled()
                val dependencyCoordinate = buildCoordinate(artifact)
                val tailText = artifact.latestVersion.takeUnless { it.isBlank() } ?: artifact.version
                val repository = artifact.repositoryId.takeUnless { it.isBlank() } ?: artifact.packaging
                var lookup = LookupElementBuilder.create(dependencyCoordinate)
                    .withPresentableText("${artifact.groupId}:${artifact.artifactId}")
                    .withTailText(if (tailText.isBlank()) "" else ":$tailText", true)
                    .withTypeText(repository, true)
                lookup = lookup
                    .withLookupString(artifact.groupId)
                    .withLookupString("${artifact.groupId}:${artifact.artifactId}")
                    .withLookupString(dependencyCoordinate)
                resultSet.addElement(lookup)
            }
            result.restartCompletionOnAnyPrefixChange()
        }

        private fun findLiteralInfo(position: PsiElement, parameters: CompletionParameters): LiteralInfo? {
            val literal = position.parents(withSelf = true)
                .firstOrNull { it is PsiLiteralExpression || it is KtStringTemplateExpression }
                ?: return null

            if (literal is KtStringTemplateExpression && literal.hasInterpolation()) {
                return null
            }

            val textRange = literal.textRange ?: return null
            val document = parameters.editor.document
            val literalText = document.getText(textRange)

            val prefixLength = when {
                literalText.startsWith("\"\"\"") -> 3
                literalText.startsWith("\"") -> 1
                else -> return null
            }

            val contentStart = textRange.startOffset + prefixLength
            val caretOffset = parameters.offset.coerceAtLeast(contentStart)
            if (caretOffset < contentStart) return null

            val valueBeforeCaret = document.getText(TextRange(contentStart, caretOffset))
            return LiteralInfo(valueBeforeCaret)
        }

        private fun buildCoordinate(artifact: MavenArtifact): String {
            val version = artifact.latestVersion.takeUnless { it.isBlank() } ?: artifact.version
            return if (version.isBlank()) {
                "${artifact.groupId}:${artifact.artifactId}"
            } else {
                "${artifact.groupId}:${artifact.artifactId}:$version"
            }
        }

        private fun fetchArtifacts(query: String): List<MavenArtifact> {
            val now = System.currentTimeMillis()
            val cached = lastCache
            if (cached != null && cached.query == query && now - cached.timestamp <= CACHE_TTL_MS) {
                return cached.artifacts
            }

            return try {
                val limit = MavenSearchSettings.getInstance().maxResults.coerceIn(5, 50)
                val artifacts = MavenCentralSearchUtil.searchByKeyword(query, limit)
                lastCache = CacheEntry(query, now, artifacts)
                artifacts
            } catch (t: Throwable) {
                t.printStackTrace()
                Logger.getInstance(GradleKtsCompletionContributor::class.java).warn("Maven completion query failed for '$query'", t)
                emptyList()
            }
        }
    }

    private data class LiteralInfo(
        val valueBeforeCaret: String
    )

    data class CacheEntry(
        val query: String,
        val timestamp: Long,
        val artifacts: List<MavenArtifact>
    )

    companion object {
        private const val MIN_QUERY_LENGTH = 2
        private const val CACHE_TTL_MS = 2_000L
    }
}

@Volatile
private var lastCache: GradleKtsCompletionContributor.CacheEntry? = null

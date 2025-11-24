package site.addzero.maven.search.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLiteralExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import site.addzero.network.call.maven.util.MavenCentralSearchUtil

/**
 * Alt+Enter intention that upgrades `groupId:artifactId:version` coordinates to the latest Maven Central release.
 */
class UpdateDependencyToLatestIntention : PsiElementBaseIntentionAction(), IntentionAction {

    override fun getFamilyName(): String = "Update dependency to latest version"

    override fun getText(): String = "Upgrade Maven dependency to latest version"

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val literalContext = findLiteralContext(element) ?: return false
        return parseCoordinates(literalContext.value) != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val literalContext = findLiteralContext(element) ?: return
        val coordinates = parseCoordinates(literalContext.value) ?: return

        val latestVersion = resolveLatestVersion(project, coordinates)
        if (latestVersion.isNullOrBlank()) {
            return
        }

        if (latestVersion == coordinates.version) {
            notify(project, "${coordinates.id} is already at the latest version ($latestVersion)")
            return
        }

        val newLiteralValue = coordinates.buildLiteral(latestVersion)
        WriteCommandAction.runWriteCommandAction(project, text, null, Runnable {
            literalContext.replaceValue(project, newLiteralValue)
        }, element.containingFile)

        notify(project, "Updated ${coordinates.id} to $latestVersion")
    }

    private fun resolveLatestVersion(project: Project, coordinates: MavenCoordinates): String? {
        var resolvedVersion: String? = null
        var error: Throwable? = null

        val completed = ProgressManager.getInstance().runProcessWithProgressSynchronously({
            try {
                resolvedVersion = MavenCentralSearchUtil.getLatestVersion(
                    coordinates.groupId,
                    coordinates.artifactId
                )
            } catch (t: Throwable) {
                error = t
            }
        }, "Resolving latest Maven version", true, project)

        if (!completed) {
            return null
        }

        if (error != null) {
            logger.warn("Failed to resolve latest version for ${coordinates.id}", error)
            error?.printStackTrace()
            notify(
                project,
                "Failed to fetch version for ${coordinates.id}: ${error?.message ?: error?.javaClass?.simpleName}",
                NotificationType.ERROR
            )
            return null
        }

        if (resolvedVersion.isNullOrBlank()) {
            notify(project, "No release information found for ${coordinates.id}", NotificationType.WARNING)
        }

        return resolvedVersion
    }

    private fun findLiteralContext(element: PsiElement): LiteralContext? {
        var current: PsiElement? = element
        while (current != null) {
            when (current) {
                is KtStringTemplateExpression -> {
                    if (current.hasInterpolation()) {
                        return null
                    }
                    val value = current.extractContent() ?: return null
                    return LiteralContext(current, value, LiteralLanguage.KOTLIN)
                }
                is PsiLiteralExpression -> {
                    val text = current.value as? String ?: return null
                    return LiteralContext(current, text, LiteralLanguage.JAVA)
                }
            }
            current = current.parent
        }
        return null
    }

    private fun KtStringTemplateExpression.extractContent(): String? {
        val raw = text
        if (raw.length < 2) return null
        return when {
            raw.startsWith("\"\"\"") && raw.endsWith("\"\"\"") -> raw.substring(3, raw.length - 3)
            raw.startsWith("\"") && raw.endsWith("\"") -> raw.substring(1, raw.length - 1)
            else -> null
        }
    }

    private fun LiteralContext.replaceValue(project: Project, newValue: String) {
        when (language) {
            LiteralLanguage.KOTLIN -> {
                val psiFactory = KtPsiFactory(project)
                val escaped = StringUtil.escapeStringCharacters(newValue)
                val newExpression = psiFactory.createExpression("\"$escaped\"")
                (literalElement as KtStringTemplateExpression).replace(newExpression)
            }
            LiteralLanguage.JAVA -> {
                val factory = JavaPsiFacade.getElementFactory(project)
                val escaped = StringUtil.escapeStringCharacters(newValue)
                val newExpression = factory.createExpressionFromText("\"$escaped\"", literalElement)
                literalElement.replace(newExpression)
            }
        }
    }

    private fun notify(project: Project, content: String, type: NotificationType = NotificationType.INFORMATION) {
        Notifications.Bus.notify(Notification("MavenSearch", "Maven Dependency", content, type), project)
    }

    private fun parseCoordinates(value: String): MavenCoordinates? {
        val firstColon = value.indexOf(':')
        val lastColon = value.lastIndexOf(':')
        if (firstColon <= 0 || lastColon <= firstColon) return null

        val versionSection = value.substring(lastColon + 1)
        val leadingWhitespace = versionSection.takeWhile { it.isWhitespace() }
        val trailingWhitespace = versionSection.takeLastWhile { it.isWhitespace() }
        val version = versionSection.trim()
        if (version.isBlank()) return null

        val secondColon = value.indexOf(':', firstColon + 1)
        val artifactEnd = if (secondColon == -1 || secondColon >= lastColon) lastColon else secondColon
        val groupId = value.substring(0, firstColon).trim()
        val artifactId = value.substring(firstColon + 1, artifactEnd).trim()
        if (groupId.isBlank() || artifactId.isBlank()) return null

        val prefixBeforeVersion = value.substring(0, lastColon + 1)
        return MavenCoordinates(
            groupId = groupId,
            artifactId = artifactId,
            version = version,
            prefixBeforeVersion = prefixBeforeVersion,
            versionLeadingWhitespace = leadingWhitespace,
            versionTrailingWhitespace = trailingWhitespace
        )
    }

    private data class LiteralContext(
        val literalElement: PsiElement,
        val value: String,
        val language: LiteralLanguage
    )

    private enum class LiteralLanguage {
        KOTLIN,
        JAVA
    }

    private data class MavenCoordinates(
        val groupId: String,
        val artifactId: String,
        val version: String,
        val prefixBeforeVersion: String,
        val versionLeadingWhitespace: String,
        val versionTrailingWhitespace: String
    ) {
        val id: String get() = "$groupId:$artifactId"

        fun buildLiteral(newVersion: String): String {
            return prefixBeforeVersion + versionLeadingWhitespace + newVersion + versionTrailingWhitespace
        }
    }

    companion object {
        private val logger = Logger.getInstance(UpdateDependencyToLatestIntention::class.java)
    }
}

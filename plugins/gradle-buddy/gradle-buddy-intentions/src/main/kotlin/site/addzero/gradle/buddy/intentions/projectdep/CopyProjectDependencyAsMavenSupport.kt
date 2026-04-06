package site.addzero.gradle.buddy.intentions.projectdep

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle
import site.addzero.gradle.buddy.migration.MavenReplacementFinder
import site.addzero.gradle.buddy.search.MavenArtifactCellRenderer
import site.addzero.network.call.maven.util.MavenArtifact
import java.awt.datatransfer.StringSelection

object CopyProjectDependencyAsMavenSupport {

    private const val SEARCH_LIMIT = 20
    private val PROJECTS_ACCESSOR_REGEX = Regex("""^projects\.[A-Za-z0-9_.]+$""")

    data class Target(
        val configType: String,
        val moduleName: String,
        val dependencyText: String,
        val canonicalModulePath: String?,
        val sourceGradleRootPath: String?
    )

    fun findTarget(project: Project, file: PsiFile, offset: Int): Target? {
        if (!file.name.endsWith(".gradle.kts") || file.name == "settings.gradle.kts") {
            return null
        }

        val element = file.findElementAt(offset) ?: return null
        val anchorFile = file.virtualFile
        return generateSequence(element) { it.parent }
            .filterIsInstance<KtCallExpression>()
            .firstNotNullOfOrNull { resolveTarget(project, it, anchorFile) }
    }

    fun execute(project: Project, editor: Editor?, target: Target) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            GradleBuddyBundle.message("intention.copy.project.dependency.as.maven.searching", target.moduleName),
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = GradleBuddyBundle.message(
                    "intention.copy.project.dependency.as.maven.searching",
                    target.moduleName
                )

                val artifacts = MavenReplacementFinder.searchArtifacts(target.moduleName, SEARCH_LIMIT)
                ApplicationManager.getApplication().invokeLater {
                    showResult(project, editor, target, artifacts)
                }
            }
        })
    }

    private fun showResult(
        project: Project,
        editor: Editor?,
        target: Target,
        artifacts: List<MavenArtifact>
    ) {
        if (artifacts.isEmpty()) {
            GradleBuddyNotifications.warn(
                project,
                GradleBuddyBundle.message("intention.copy.project.dependency.as.maven.not.found.title"),
                GradleBuddyBundle.message(
                    "intention.copy.project.dependency.as.maven.not.found.content",
                    target.dependencyText
                )
            )
            return
        }

        if (artifacts.size == 1) {
            copyArtifact(project, target, artifacts.first())
            return
        }

        val popup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(artifacts)
            .setTitle(
                GradleBuddyBundle.message(
                    "intention.copy.project.dependency.as.maven.popup.title",
                    target.moduleName
                )
            )
            .setRenderer(MavenArtifactCellRenderer())
            .setItemChosenCallback { artifact ->
                copyArtifact(project, target, artifact)
            }
            .createPopup()

        if (editor != null) {
            popup.showInBestPositionFor(editor)
        } else {
            popup.showCenteredInCurrentWindow(project)
        }
    }

    private fun copyArtifact(project: Project, target: Target, artifact: MavenArtifact) {
        val version = artifact.latestVersion.ifBlank { artifact.version.ifBlank { "+" } }
        val dependency = """${target.configType}("${artifact.groupId}:${artifact.artifactId}:$version")"""
        CopyPasteManager.getInstance().setContents(StringSelection(dependency))

        GradleBuddyNotifications.info(
            project,
            GradleBuddyBundle.message("intention.copy.project.dependency.as.maven.copied.title"),
            GradleBuddyBundle.message(
                "intention.copy.project.dependency.as.maven.copied.content",
                dependency
            )
        )
    }

    private fun resolveTarget(project: Project, callExpression: KtCallExpression, anchorFile: com.intellij.openapi.vfs.VirtualFile?): Target? {
        val configType = callExpression.calleeExpression?.text?.trim().orEmpty()
        if (configType.isBlank() || !isInsideDependenciesBlock(callExpression)) {
            return null
        }

        val argumentExpression = callExpression.valueArguments.singleOrNull()?.getArgumentExpression() ?: return null
        return when (argumentExpression) {
            is KtCallExpression -> resolveProjectCall(project, configType, argumentExpression, anchorFile)
            is KtDotQualifiedExpression -> resolveProjectAccessor(project, configType, argumentExpression, anchorFile)
            else -> null
        }
    }

    private fun resolveProjectCall(
        project: Project,
        configType: String,
        projectCall: KtCallExpression,
        anchorFile: com.intellij.openapi.vfs.VirtualFile?
    ): Target? {
        if (projectCall.calleeExpression?.text != "project") {
            return null
        }

        val stringExpression = projectCall.valueArguments.singleOrNull()?.getArgumentExpression() as? KtStringTemplateExpression
            ?: return null
        val modulePath = extractLiteralString(stringExpression)?.trim()?.takeIf { it.startsWith(":") } ?: return null
        val moduleName = modulePath.substringAfterLast(':').takeIf { it.isNotBlank() } ?: return null

        return Target(
            configType = configType,
            moduleName = moduleName,
            dependencyText = """project("$modulePath")""",
            canonicalModulePath = modulePath,
            sourceGradleRootPath = ProjectModuleResolver.findOwningRoot(project, anchorFile)?.path
        )
    }

    private fun resolveProjectAccessor(
        project: Project,
        configType: String,
        expression: KtDotQualifiedExpression,
        anchorFile: com.intellij.openapi.vfs.VirtualFile?
    ): Target? {
        val topExpression = findTopDotExpression(expression)
        val accessor = topExpression.text.trim()
        if (!PROJECTS_ACCESSOR_REGEX.matches(accessor)) {
            return null
        }
        if (!isDependencyLikeArgument(topExpression)) {
            return null
        }

        val module = ProjectModuleResolver.findByTypeSafeAccessor(project, accessor, anchorFile)
        val modulePath = module?.path
        val moduleName = modulePath
            ?.substringAfterLast(':')
            ?.takeIf { it.isNotBlank() }
            ?: accessor.substringAfterLast('.').camelCaseToKebabCase()

        return Target(
            configType = configType,
            moduleName = moduleName,
            dependencyText = accessor,
            canonicalModulePath = modulePath,
            sourceGradleRootPath = module?.rootDir?.path ?: ProjectModuleResolver.findOwningRoot(project, anchorFile)?.path
        )
    }

    private fun extractLiteralString(expression: KtStringTemplateExpression): String? {
        if (expression.entries.any { it !is KtLiteralStringTemplateEntry }) {
            return null
        }
        return expression.entries.joinToString(separator = "") { it.text }
    }

    private fun findTopDotExpression(expression: KtDotQualifiedExpression): KtDotQualifiedExpression {
        var current = expression
        while (current.parent is KtDotQualifiedExpression) {
            current = current.parent as KtDotQualifiedExpression
        }
        return current
    }

    private fun isDependencyLikeArgument(expression: KtDotQualifiedExpression): Boolean {
        val callExpression = expression.parent as? KtCallExpression ?: return false
        return callExpression.valueArguments.singleOrNull()?.getArgumentExpression() == expression
    }

    private fun isInsideDependenciesBlock(element: com.intellij.psi.PsiElement): Boolean {
        var current: com.intellij.psi.PsiElement? = element
        while (current != null) {
            if (current is KtCallExpression && current.calleeExpression?.text == "dependencies") {
                return true
            }
            current = current.parent
        }
        return false
    }

    private fun String.camelCaseToKebabCase(): String {
        return fold(StringBuilder()) { acc, char ->
            when {
                char.isUpperCase() && acc.isNotEmpty() -> acc.append('-').append(char.lowercaseChar())
                else -> acc.append(char.lowercaseChar())
            }
        }.toString()
    }

    private object GradleBuddyNotifications {
        fun info(project: Project, title: String, content: String) {
            com.intellij.notification.NotificationGroupManager.getInstance()
                .getNotificationGroup("GradleBuddy")
                .createNotification(title, content, com.intellij.notification.NotificationType.INFORMATION)
                .notify(project)
        }

        fun warn(project: Project, title: String, content: String) {
            com.intellij.notification.NotificationGroupManager.getInstance()
                .getNotificationGroup("GradleBuddy")
                .createNotification(title, content, com.intellij.notification.NotificationType.WARNING)
                .notify(project)
        }
    }
}

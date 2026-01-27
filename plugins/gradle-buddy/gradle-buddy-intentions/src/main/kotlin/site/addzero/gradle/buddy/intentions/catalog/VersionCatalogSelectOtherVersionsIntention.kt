package site.addzero.gradle.buddy.intentions.catalog

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiFile
import site.addzero.gradle.buddy.intentions.select.VersionSelectionDialog
import site.addzero.network.call.maven.util.MavenCentralSearchUtil

class VersionCatalogSelectOtherVersionsIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

    override fun getFamilyName(): String = "Gradle Buddy"

    override fun getText(): String = "(Gradle Buddy) Select other versions"

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (!file.name.endsWith(".toml")) return false

        val virtualFile = file.virtualFile
        if (virtualFile != null && !virtualFile.path.contains("/gradle/")) {
            return false
        }

        val offset = editor?.caretModel?.offset ?: return false
        val element = file.findElementAt(offset) ?: return false
        return VersionCatalogDependencyHelper.detectCatalogDependencyAt(element) != null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        val offset = editor?.caretModel?.offset ?: return
        val element = file.findElementAt(offset) ?: return
        val dependencyInfo = VersionCatalogDependencyHelper.detectCatalogDependencyAt(element) ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Fetching versions...", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                val versions = runCatching {
                    MavenCentralSearchUtil.searchAllVersions(
                        dependencyInfo.groupId,
                        dependencyInfo.artifactId,
                        50
                    ).map { it.latestVersion }.distinct().sortedDescending()
                }.getOrElse { emptyList() }

                ApplicationManager.getApplication().invokeLater {
                    val allVersions = ensureVersionIncluded(versions, dependencyInfo.currentVersion)
                    if (allVersions.isEmpty()) {
                        Messages.showWarningDialog(
                            project,
                            "Could not load versions for ${dependencyInfo.groupId}:${dependencyInfo.artifactId}",
                            "Select Version Failed"
                        )
                        return@invokeLater
                    }

                    val dialog = VersionSelectionDialog(
                        project,
                        "Select Version - ${dependencyInfo.groupId}:${dependencyInfo.artifactId}",
                        allVersions,
                        dependencyInfo.currentVersion
                    )
                    if (!dialog.showAndGet()) return@invokeLater
                    val selectedVersion = dialog.getSelectedVersion() ?: return@invokeLater
                    if (selectedVersion == dependencyInfo.currentVersion) return@invokeLater

                    WriteCommandAction.runWriteCommandAction(project) {
                        VersionCatalogDependencyHelper.updateCatalogDependency(file, dependencyInfo, selectedVersion)
                    }
                }
            }
        })
    }

    private fun ensureVersionIncluded(versions: List<String>, currentVersion: String): List<String> {
        if (versions.contains(currentVersion)) return versions
        return listOf(currentVersion) + versions
    }
}

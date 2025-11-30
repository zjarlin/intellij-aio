package site.addzero.gradle.buddy.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import site.addzero.network.call.maven.util.MavenCentralSearchUtil

/**
 * Gradle KTS Update Dependency Intention
 * 
 * This intention action allows updating Gradle KTS dependencies in .gradle.kts files to their latest versions
 * by fetching version information from Maven Central.
 */
class GradleKtsUpdateDependencyIntention : PsiElementBaseIntentionAction(), IntentionAction {

    override fun getFamilyName(): String = "Gradle buddy"

    override fun getText(): String = "Update dependency to latest version"
    
    fun getDescription(): String = "Fetches the latest version from Maven Central and updates the Gradle KTS dependency declaration."

    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html("Fetches the latest version from Maven Central and updates the Gradle KTS dependency.")
    }

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val file = element.containingFile ?: return false
        val fileName = file.name

        return when {
            fileName.endsWith(".gradle.kts") -> detectGradleKtsDependency(element) != null
            else -> false
        }
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val file = element.containingFile ?: return
        val fileName = file.name

        val dependencyInfo = when {
            fileName.endsWith(".gradle.kts") -> detectGradleKtsDependency(element)
            else -> null
        } ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Fetching latest version...", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                val latestVersion = runCatching {
                    MavenCentralSearchUtil.getLatestVersion(dependencyInfo.groupId, dependencyInfo.artifactId)
                }.getOrNull()

                ApplicationManager.getApplication().invokeLater {
                    if (latestVersion == null) {
                        Messages.showWarningDialog(
                            project,
                            "Could not find latest version for ${dependencyInfo.groupId}:${dependencyInfo.artifactId}",
                            "Update Failed"
                        )
                        return@invokeLater
                    }

                    if (latestVersion == dependencyInfo.currentVersion) {
                        Messages.showInfoMessage(
                            project,
                            "Already at latest version: $latestVersion",
                            "No Update Needed"
                        )
                        return@invokeLater
                    }

                    WriteCommandAction.runWriteCommandAction(project) {
                        replaceVersion(file, dependencyInfo, latestVersion)
                    }
                }
            }
        })
    }

    private fun replaceVersion(file: PsiFile, info: DependencyInfo, newVersion: String) {
        val document = file.viewProvider.document ?: return
        val text = document.text

        val oldText = info.fullMatch
        val newText = oldText.replace(info.currentVersion, newVersion)

        val startOffset = text.indexOf(oldText, info.approximateOffset.coerceAtLeast(0))
        if (startOffset >= 0) {
            document.replaceString(startOffset, startOffset + oldText.length, newText)
        }
    }

    private fun detectGradleKtsDependency(element: PsiElement): DependencyInfo? {
        val lineText = getLineText(element)

        val pattern = Regex("""(\w+)\s*\(\s*["']([^:]+):([^:]+):([^"']+)["']\s*\)""")
        val match = pattern.find(lineText) ?: return null

        val (_, groupId, artifactId, version) = match.destructured

        return DependencyInfo(
            groupId = groupId,
            artifactId = artifactId,
            currentVersion = version,
            fullMatch = match.value,
            approximateOffset = element.textOffset - 100
        )
    }

    private fun getLineText(element: PsiElement): String {
        val file = element.containingFile ?: return ""
        val document = file.viewProvider.document ?: return ""
        val offset = element.textOffset
        val lineNumber = document.getLineNumber(offset)
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)
        return document.getText(TextRange(lineStart, lineEnd))
    }

    private data class DependencyInfo(
        val groupId: String,
        val artifactId: String,
        val currentVersion: String,
        val fullMatch: String,
        val approximateOffset: Int
    )
}

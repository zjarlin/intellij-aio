package site.addzero.gradle.buddy.intentions.select

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
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import site.addzero.gradle.buddy.intentions.catalog.VersionCatalogDependencyHelper
import site.addzero.network.call.maven.util.MavenCentralSearchUtil

class SelectOtherVersionsIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

    override fun getFamilyName(): String = "Gradle Buddy"

    override fun getText(): String = "(Gradle Buddy) Select other versions"

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (!file.name.endsWith(".gradle.kts")) return false

        val offset = editor?.caretModel?.offset ?: return false
        val element = file.findElementAt(offset) ?: return false

        if (detectHardcodedDependency(element) != null) return true
        return detectCatalogReference(project, element) != null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        val offset = editor?.caretModel?.offset ?: return
        val element = file.findElementAt(offset) ?: return

        val hardcodedDependency = detectHardcodedDependency(element)
        if (hardcodedDependency != null) {
            selectVersion(
                project,
                hardcodedDependency.groupId,
                hardcodedDependency.artifactId,
                hardcodedDependency.currentVersion
            ) { selected ->
                WriteCommandAction.runWriteCommandAction(project) {
                    replaceHardcodedVersion(file, hardcodedDependency, selected)
                }
            }
            return
        }

        val catalogTarget = detectCatalogReference(project, element) ?: return
        selectVersion(
            project,
            catalogTarget.info.groupId,
            catalogTarget.info.artifactId,
            catalogTarget.info.currentVersion
        ) { selected ->
            WriteCommandAction.runWriteCommandAction(project) {
                VersionCatalogDependencyHelper.updateCatalogDependency(
                    catalogTarget.catalogFile,
                    catalogTarget.info,
                    selected
                )
            }
        }
    }

    private fun selectVersion(
        project: Project,
        groupId: String,
        artifactId: String,
        currentVersion: String,
        onSelected: (String) -> Unit
    ) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Fetching versions...", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                val versions = runCatching {
                    MavenCentralSearchUtil.searchAllVersions(groupId, artifactId, 50)
                        .map { it.latestVersion }
                        .distinct()
                        .sortedDescending()
                }.getOrElse { emptyList() }

                ApplicationManager.getApplication().invokeLater {
                    val allVersions = ensureVersionIncluded(versions, currentVersion)
                    if (allVersions.isEmpty()) {
                        Messages.showWarningDialog(
                            project,
                            "Could not load versions for $groupId:$artifactId",
                            "Select Version Failed"
                        )
                        return@invokeLater
                    }

                    val dialog = VersionSelectionDialog(
                        project,
                        "Select Version - $groupId:$artifactId",
                        allVersions,
                        currentVersion
                    )
                    if (!dialog.showAndGet()) return@invokeLater
                    val selectedVersion = dialog.getSelectedVersion() ?: return@invokeLater
                    if (selectedVersion == currentVersion) return@invokeLater
                    onSelected(selectedVersion)
                }
            }
        })
    }

    private fun ensureVersionIncluded(versions: List<String>, currentVersion: String): List<String> {
        if (versions.contains(currentVersion)) return versions
        return listOf(currentVersion) + versions
    }

    private fun detectHardcodedDependency(element: PsiElement): HardcodedDependencyInfo? {
        val lineText = getLineText(element)

        val dependencyPattern = Regex("""(\w+)\s*\(\s*["']([^:]+):([^:]+):([^"']+)["']\s*\)""")
        val depMatch = dependencyPattern.find(lineText) ?: return null
        val (_, groupId, artifactId, version) = depMatch.destructured
        return HardcodedDependencyInfo(
            groupId = groupId,
            artifactId = artifactId,
            currentVersion = version,
            fullMatch = depMatch.value,
            approximateOffset = element.textOffset - 100
        )
    }

    private fun detectCatalogReference(project: Project, element: PsiElement): CatalogTarget? {
        val lineText = getLineText(element)
        val accessorMatch = Regex("""\blibs\.([A-Za-z0-9_.]+)""").find(lineText) ?: return null
        val accessor = accessorMatch.groupValues[1]
        val resolved = VersionCatalogDependencyHelper.findCatalogDependencyByAccessor(project, accessor) ?: return null
        return CatalogTarget(resolved.first, resolved.second)
    }

    private fun replaceHardcodedVersion(file: PsiFile, info: HardcodedDependencyInfo, newVersion: String) {
        val document = file.viewProvider.document ?: return
        val text = document.text
        val oldText = info.fullMatch
        val newText = oldText.replace(info.currentVersion, newVersion)
        val startOffset = text.indexOf(oldText, info.approximateOffset.coerceAtLeast(0))
        if (startOffset >= 0) {
            document.replaceString(startOffset, startOffset + oldText.length, newText)
        }
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

    private data class HardcodedDependencyInfo(
        val groupId: String,
        val artifactId: String,
        val currentVersion: String,
        val fullMatch: String,
        val approximateOffset: Int
    )

    private data class CatalogTarget(
        val catalogFile: PsiFile,
        val info: VersionCatalogDependencyHelper.CatalogDependencyInfo
    )
}

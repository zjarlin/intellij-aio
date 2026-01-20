package site.addzero.gradle.buddy.intentions.catalog

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
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
 * Version Catalog Update Dependency Intention
 *
 * 在 libs.versions.toml 文件中升级依赖版本
 * 支持格式:
 * - junit-jupiter-api = { group = "org.junit.jupiter", name = "junit-jupiter-api", version.ref = "jupiter" }
 * - junit-jupiter-api = { module = "org.junit.jupiter:junit-jupiter-api", version.ref = "jupiter" }
 * - junit-jupiter-api = { module = "org.junit.jupiter:junit-jupiter-api", version = "5.10.0" }
 *
 * Priority: HIGH - 在版本目录文件中优先显示此intention
 */
class VersionCatalogUpdateDependencyIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

    override fun getFamilyName(): String = "Gradle buddy"

    override fun getText(): String = "Update dependency to latest version"

    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html("Fetches the latest version from Maven Central and updates the version catalog dependency.")
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (file.name != "libs.versions.toml") return false

        val offset = editor?.caretModel?.offset ?: 0
        val element = file.findElementAt(offset)
        return element != null && detectCatalogDependency(element) != null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        if (file.name != "libs.versions.toml") return

        val offset = editor?.caretModel?.offset ?: 0
        val element = file.findElementAt(offset) ?: return

        val dependencyInfo = detectCatalogDependency(element) ?: return

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
                        updateCatalogDependency(file, dependencyInfo, latestVersion)
                    }
                }
            }
        })
    }

    private fun updateCatalogDependency(file: PsiFile, info: CatalogDependencyInfo, newVersion: String) {
        val document = file.viewProvider.document ?: return
        val text = document.text

        if (info.isVersionRef) {
            // 查找并更新版本引用
            val versionPattern = Regex("""${Regex.escape(info.versionKey)}\s*=\s*["']${Regex.escape(info.currentVersion)}["']""")
            val versionMatch = versionPattern.find(text)
            if (versionMatch != null) {
                val newVersionText = versionMatch.value.replace(info.currentVersion, newVersion)
                val startOffset = text.indexOf(versionMatch.value)
                if (startOffset >= 0) {
                    document.replaceString(startOffset, startOffset + versionMatch.value.length, newVersionText)
                }
            }
        } else {
            // 直接更新版本
            val oldText = "${info.key} = ${info.originalValue}"
            val newText = oldText.replace(info.currentVersion, newVersion)
            val startOffset = text.indexOf(oldText, info.approximateOffset.coerceAtLeast(0))
            if (startOffset >= 0) {
                document.replaceString(startOffset, startOffset + oldText.length, newText)
            }
        }
    }

    private fun detectCatalogDependency(element: PsiElement): CatalogDependencyInfo? {
        val text = getLineText(element)

        // 格式1: junit-jupiter-api = { group = "org.junit.jupiter", name = "junit-jupiter-api", version.ref = "jupiter" }
        val groupPattern = Regex("""(\w+(?:-\w+)*)\s*=\s*\{\s*group\s*=\s*"([^"]+)"\s*,\s*name\s*=\s*"([^"]+)"\s*,\s*version\.ref\s*=\s*"([^"]+)"\s*\}""")
        val groupMatch = groupPattern.find(text)
        if (groupMatch != null) {
            val (_, key, groupId, artifactId, versionRef) = groupMatch.destructured
            val currentVersion = findVersionRef(text, versionRef)
            if (currentVersion != null) {
                return CatalogDependencyInfo(
                    key = key,
                    groupId = groupId,
                    artifactId = artifactId,
                    currentVersion = currentVersion,
                    versionKey = versionRef,
                    isVersionRef = true,
                    originalValue = groupMatch.value,
                    approximateOffset = element.textOffset - 100
                )
            }
        }

        // 格式2: junit-jupiter-api = { module = "org.junit.jupiter:junit-jupiter-api", version.ref = "jupiter" }
        val modulePattern = Regex("""(\w+(?:-\w+)*)\s*=\s*\{\s*module\s*=\s*"([^:]+):([^"]+)"\s*,\s*version\.ref\s*=\s*"([^"]+)"\s*\}""")
        val moduleMatch = modulePattern.find(text)
        if (moduleMatch != null) {
            val (_, key, groupId, artifactId, versionRef) = moduleMatch.destructured
            val currentVersion = findVersionRef(text, versionRef)
            if (currentVersion != null) {
                return CatalogDependencyInfo(
                    key = key,
                    groupId = groupId,
                    artifactId = artifactId,
                    currentVersion = currentVersion,
                    versionKey = versionRef,
                    isVersionRef = true,
                    originalValue = moduleMatch.value,
                    approximateOffset = element.textOffset - 100
                )
            }
        }

        // 格式3: junit-jupiter-api = { module = "org.junit.jupiter:junit-jupiter-api", version = "5.10.0" }
        val directVersionPattern = Regex("""(\w+(?:-\w+)*)\s*=\s*\{\s*module\s*=\s*"([^:]+):([^"]+)"\s*,\s*version\s*=\s*"([^"]+)"\s*\}""")
        val directVersionMatch = directVersionPattern.find(text)
        if (directVersionMatch != null) {
            val (_, key, groupId, artifactId, version) = directVersionMatch.destructured
            return CatalogDependencyInfo(
                key = key,
                groupId = groupId,
                artifactId = artifactId,
                currentVersion = version,
                versionKey = "",
                isVersionRef = false,
                originalValue = directVersionMatch.value,
                approximateOffset = element.textOffset - 100
            )
        }

        return null
    }

    private fun findVersionRef(text: String, versionKey: String): String? {
        val versionPattern = Regex("""${Regex.escape(versionKey)}\s*=\s*["']([^"']+)["']""")
        val match = versionPattern.find(text)
        return match?.groupValues?.get(1)
    }

    private fun getLineText(element: PsiElement): String {
        val file = element.containingFile ?: return ""
        val offset = element.textOffset
        val document = file.viewProvider.document ?: return ""
        val lineNumber = document.getLineNumber(offset)
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)
        return document.getText(TextRange(lineStart, lineEnd))
    }

    private data class CatalogDependencyInfo(
        val key: String,
        val groupId: String,
        val artifactId: String,
        val currentVersion: String,
        val versionKey: String,
        val isVersionRef: Boolean,
        val originalValue: String,
        val approximateOffset: Int
    )
}

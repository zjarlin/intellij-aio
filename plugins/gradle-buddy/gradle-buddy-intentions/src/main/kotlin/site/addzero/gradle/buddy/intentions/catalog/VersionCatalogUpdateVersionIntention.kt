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
 * Version Catalog Update Version Variable Intention
 *
 * 在 [versions] 部分升级版本变量
 * 支持格式:
 * - kotlin = "1.9.0"
 * - kotlin = '1.9.0'
 *
 * 会查找所有引用此版本变量的依赖，并从中提取 groupId 和 artifactId 来查询最新版本
 *
 * Priority: HIGH - 在版本目录文件中优先显示此intention
 */
class VersionCatalogUpdateVersionIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

    override fun getFamilyName(): String = "Gradle buddy"

    override fun getText(): String = "(Gradle Buddy) Update version variable to latest"

    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html("Fetches the latest version from Maven Central and updates the version variable in [versions] section.")
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        // 检查是否为 TOML 文件且在 gradle 目录下
        if (!file.name.endsWith(".toml")) return false

        val virtualFile = file.virtualFile
        if (virtualFile != null) {
            val path = virtualFile.path
            if (!path.contains("/gradle/")) return false
        }

        val offset = editor?.caretModel?.offset ?: 0
        val element = file.findElementAt(offset) ?: return false

        val versionInfo = detectVersionVariable(element, file.text)
        return versionInfo != null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        val offset = editor?.caretModel?.offset ?: 0
        val element = file.findElementAt(offset) ?: return

        val versionInfo = detectVersionVariable(element, file.text) ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Fetching latest version...", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                // 查找引用此版本变量的依赖
                val dependencies = findDependenciesUsingVersion(file.text, versionInfo.versionKey)

                if (dependencies.isEmpty()) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showWarningDialog(
                            project,
                            "No dependencies found using version variable '${versionInfo.versionKey}'",
                            "Update Failed"
                        )
                    }
                    return
                }

                // 使用第一个依赖来查询最新版本
                val firstDep = dependencies.first()
                val latestVersion = runCatching {
                    MavenCentralSearchUtil.getLatestVersion(firstDep.groupId, firstDep.artifactId)
                }.getOrNull()

                ApplicationManager.getApplication().invokeLater {
                    if (latestVersion == null) {
                        Messages.showWarningDialog(
                            project,
                            "Could not find latest version for ${firstDep.groupId}:${firstDep.artifactId}",
                            "Update Failed"
                        )
                        return@invokeLater
                    }

                    if (latestVersion == versionInfo.currentVersion) {
                        Messages.showInfoMessage(
                            project,
                            "Already at latest version: $latestVersion\n\nUsed by ${dependencies.size} dependencies:\n${dependencies.take(5).joinToString("\n") { "- ${it.key}" }}${if (dependencies.size > 5) "\n..." else ""}",
                            "No Update Needed"
                        )
                        return@invokeLater
                    }

                    // 显示确认对话框
                    val dependencyList = dependencies.take(10).joinToString("\n") { "- ${it.key}" } +
                        if (dependencies.size > 10) "\n... and ${dependencies.size - 10} more" else ""

                    val result = Messages.showYesNoDialog(
                        project,
                        "Update version '${versionInfo.versionKey}' from ${versionInfo.currentVersion} to $latestVersion?\n\nThis will affect ${dependencies.size} dependencies:\n$dependencyList",
                        "Confirm Version Update",
                        Messages.getQuestionIcon()
                    )

                    if (result == Messages.YES) {
                        WriteCommandAction.runWriteCommandAction(project) {
                            updateVersionVariable(file, versionInfo, latestVersion)
                        }
                    }
                }
            }
        })
    }

    private fun updateVersionVariable(file: PsiFile, info: VersionInfo, newVersion: String) {
        val document = file.viewProvider.document ?: return
        val text = document.text

        // 查找并更新版本定义
        val versionPattern = Regex("""${Regex.escape(info.versionKey)}\s*=\s*["']${Regex.escape(info.currentVersion)}["']""")
        val versionMatch = versionPattern.find(text)
        if (versionMatch != null) {
            val newVersionText = versionMatch.value.replace(info.currentVersion, newVersion)
            val startOffset = text.indexOf(versionMatch.value)
            if (startOffset >= 0) {
                document.replaceString(startOffset, startOffset + versionMatch.value.length, newVersionText)
            }
        }
    }

    private fun detectVersionVariable(element: PsiElement, fullText: String): VersionInfo? {
        val lineText = getLineText(element)

        // 匹配格式: kotlin = "1.9.0" 或 kotlin = '1.9.0'
        val versionPattern = Regex("""(\w+(?:-\w+)*)\s*=\s*["']([^"']+)["']""")
        val match = versionPattern.find(lineText) ?: return null

        val (versionKey, currentVersion) = match.destructured

        // 检查是否在 [versions] 部分
        val lineNumber = getLineNumber(element)
        if (!isInVersionsSection(fullText, lineNumber)) {
            return null
        }

        return VersionInfo(
            versionKey = versionKey,
            currentVersion = currentVersion
        )
    }

    private fun isInVersionsSection(fullText: String, lineNumber: Int): Boolean {
        val lines = fullText.lines()
        var inVersionsSection = false

        for (i in 0 until lineNumber.coerceAtMost(lines.size)) {
            val line = lines[i].trim()
            when {
                line == "[versions]" -> inVersionsSection = true
                line.startsWith("[") && line.endsWith("]") && line != "[versions]" -> inVersionsSection = false
            }
        }

        return inVersionsSection
    }

    private fun findDependenciesUsingVersion(fullText: String, versionKey: String): List<DependencyRef> {
        val dependencies = mutableListOf<DependencyRef>()

        // 格式1: group = "...", name = "...", version.ref = "versionKey"
        val groupPattern = Regex("""(\w+(?:-\w+)*)\s*=\s*\{\s*group\s*=\s*"([^"]+)"\s*,\s*name\s*=\s*"([^"]+)"\s*,\s*version\.ref\s*=\s*"${Regex.escape(versionKey)}"\s*\}""")
        groupPattern.findAll(fullText).forEach { match ->
            val (key, groupId, artifactId) = match.destructured
            dependencies.add(DependencyRef(key, groupId, artifactId))
        }

        // 格式2: module = "group:artifact", version.ref = "versionKey"
        val modulePattern = Regex("""(\w+(?:-\w+)*)\s*=\s*\{\s*module\s*=\s*"([^:]+):([^"]+)"\s*,\s*version\.ref\s*=\s*"${Regex.escape(versionKey)}"\s*\}""")
        modulePattern.findAll(fullText).forEach { match ->
            val (key, groupId, artifactId) = match.destructured
            dependencies.add(DependencyRef(key, groupId, artifactId))
        }

        return dependencies
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

    private fun getLineNumber(element: PsiElement): Int {
        val file = element.containingFile ?: return 0
        val offset = element.textOffset
        val document = file.viewProvider.document ?: return 0
        return document.getLineNumber(offset)
    }

    private data class VersionInfo(
        val versionKey: String,
        val currentVersion: String
    )

    private data class DependencyRef(
        val key: String,
        val groupId: String,
        val artifactId: String
    )
}

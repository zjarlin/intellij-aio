package site.addzero.gradle.buddy.intentions.update

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
 * Gradle KTS 依赖更新意图操作
 *
 * 此意图操作允许通过从 Maven Central 获取版本信息
 * 将 .gradle.kts 文件中的 Gradle KTS 依赖更新到最新版本。
 *
 * 优先级：高 - 在依赖声明时优先显示此意图操作
 */
class GradleKtsUpdateDependencyIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

    override fun getFamilyName(): String = "Gradle Buddy"

    override fun getText(): String = "update dependency to the latest version"

    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html("从 Maven Central 获取最新版本并更新 Gradle KTS 依赖。")
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (!file.name.endsWith(".gradle.kts")) return false

        // 查找当前光标位置并检查该位置是否有依赖
        val offset = editor?.caretModel?.offset ?: 0
        val element = file.findElementAt(offset)
        return element != null && detectGradleKtsDependency(element) != null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        if (!file.name.endsWith(".gradle.kts")) return

        val offset = editor?.caretModel?.offset ?: 0
        val element = file.findElementAt(offset) ?: return

        val dependencyInfo = detectGradleKtsDependency(element) ?: return
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "正在获取最新版本...", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                val latestVersion = runCatching {
                    if (dependencyInfo.isGradlePlugin) {
                        // 对于 Gradle 插件，使用插件 ID 查询
                        getLatestGradlePluginVersion(dependencyInfo.groupId)
                    } else {
                        // 对于普通依赖，使用 Maven Central 查询
                        MavenCentralSearchUtil.getLatestVersion(dependencyInfo.groupId, dependencyInfo.artifactId)
                    }
                }.getOrNull()

                ApplicationManager.getApplication().invokeLater {
                    if (latestVersion == null) {
                        val identifier = if (dependencyInfo.isGradlePlugin) {
                            dependencyInfo.groupId // 插件 ID
                        } else {
                            "${dependencyInfo.groupId}:${dependencyInfo.artifactId}"
                        }
                        Messages.showWarningDialog(
                            project,
                            "无法找到 $identifier 的最新版本",
                            "更新失败"
                        )
                        return@invokeLater
                    }

                    if (latestVersion == dependencyInfo.currentVersion) {
                        Messages.showInfoMessage(
                            project,
                            "已经是最新版本: $latestVersion",
                            "无需更新"
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

    // 替换版本号
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

    // 检测 Gradle KTS 依赖
    private fun detectGradleKtsDependency(element: PsiElement): DependencyInfo? {
        val lineText = getLineText(element)

        // 格式1: implementation("group:artifact:version")
        val dependencyPattern = Regex("""(\w+)\s*\(\s*["']([^:]+):([^:]+):([^"']+)["']\s*\)""")
        val depMatch = dependencyPattern.find(lineText)
        if (depMatch != null) {
            val (_, groupId, artifactId, version) = depMatch.destructured
            return DependencyInfo(
                groupId = groupId,
                artifactId = artifactId,
                currentVersion = version,
                fullMatch = depMatch.value,
                approximateOffset = element.textOffset - 100
            )
        }

        // 格式2: id("plugin.id") version "version" (settings.gradle.kts 插件)
        val pluginPattern = Regex("""id\s*\(\s*["']([^"']+)["']\s*\)\s+version\s+["']([^"']+)["']""")
        val pluginMatch = pluginPattern.find(lineText)
        if (pluginMatch != null) {
            val pluginId = pluginMatch.groupValues[1]
            val version = pluginMatch.groupValues[2]

            // 使用插件 ID 作为 groupId 和 artifactId（用于显示）
            // 实际查询时需要特殊处理
            return DependencyInfo(
                groupId = pluginId, // 插件 ID
                artifactId = "", // 插件没有 artifactId
                currentVersion = version,
                fullMatch = pluginMatch.value,
                approximateOffset = element.textOffset - 100,
                isGradlePlugin = true
            )
        }

        return null
    }

    /**
     * 获取 Gradle 插件的最新版本
     * 通过 Gradle Plugin Portal API 查询
     */
    private fun getLatestGradlePluginVersion(pluginId: String): String? {
        return try {
            val urlString = "https://plugins.gradle.org/m2/${pluginId.replace('.', '/')}/$pluginId.gradle.plugin/maven-metadata.xml"
            val uri = java.net.URI(urlString)
            val connection = uri.toURL().openConnection()
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val xml = connection.getInputStream().bufferedReader().readText()

            // 简单的 XML 解析，提取 <latest> 或 <release> 标签
            val latestPattern = Regex("""<latest>([^<]+)</latest>""")
            val releasePattern = Regex("""<release>([^<]+)</release>""")

            latestPattern.find(xml)?.groupValues?.get(1)
                ?: releasePattern.find(xml)?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }

    private fun getLineText(element: PsiElement): String {
        val file = element.containingFile ?: return ""
        val offset = element.textOffset
        return getLineTextFromFile(file, offset)
    }

    private fun getLineTextFromFile(file: PsiFile, offset: Int): String {
        val document = file.viewProvider.document ?: return ""
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
        val approximateOffset: Int,
        val isGradlePlugin: Boolean = false
    )
}

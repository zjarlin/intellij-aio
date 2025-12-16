package site.addzero.gradle.buddy.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Gradle KTS Plugin to TOML Intention
 *
 * This intention action allows converting Gradle plugin declarations from Groovy DSL format
 * to TOML format in build.gradle.kts files.
 *
 * Priority: HIGH - 在插件声明上时优先显示此intention
 *
 * @description Converts the Gradle plugin declaration to TOML [plugins] section format.
 */
class GradleKtsPluginToTomlIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

    override fun getFamilyName(): String = "Gradle buddy"

    override fun getText(): String = "Convert plugin declaration to TOML format"

    override fun startInWriteAction(): Boolean = true

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html("Converts the Gradle plugin declaration to TOML format.")
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (!file.name.endsWith(".gradle.kts")) return false

        val offset = editor?.caretModel?.offset ?: 0
        val element = file.findElementAt(offset)
        return element != null && detectGradleKtsPlugin(element) != null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        if (!file.name.endsWith(".gradle.kts")) return

        val offset = editor?.caretModel?.offset ?: 0
        val element = file.findElementAt(offset) ?: return

        val pluginInfo = detectGradleKtsPlugin(element) ?: return

        WriteCommandAction.runWriteCommandAction(project) {
            convertPluginToTomlComment(file, pluginInfo)
        }
    }

    private fun convertPluginToTomlComment(file: PsiFile, info: PluginInfo) {
        val document = file.viewProvider.document ?: return

        // 生成 TOML 格式的注释
        val tomlFormat = generateTomlFormat(info)

        // 在当前行之后插入 TOML 格式的注释
        val currentLine = IntentionUtils.getLineNumber(document, info.element.textOffset)
        val lineEnd = document.getLineEndOffset(currentLine)
        val nextLineStart = if (currentLine + 1 < document.lineCount) {
            document.getLineStartOffset(currentLine + 1)
        } else {
            lineEnd
        }

        val comment = "\n# TOML format:\n# $tomlFormat"
        document.insertString(nextLineStart, comment)
    }

    private fun generateTomlFormat(info: PluginInfo): String {
        val id = info.id
        val version = info.version

        // 生成版本目录库名称
        val libraryName = id.replace(".", "-")

        return if (info.isKotlinPlugin) {
            """[plugins]
$id = { id = "$id", version.ref = "$version" }"""
        } else {
            """[versions]
$libraryName = "$version"

[plugins]
$id = { id = "$id", version.ref = "$libraryName" }"""
        }
    }

    private fun detectGradleKtsPlugin(element: PsiElement): PluginInfo? {
        var current: PsiElement? = element

        // 向上遍历查找 KtCallExpression
        while (current != null) {
            if (current is KtCallExpression) {
                val callExpression = current

                // 检查是否是插件声明
                if (isPluginDeclaration(callExpression)) {
                    return extractPluginInfo(callExpression)
                }
            }
            current = current.parent
        }

        return null
    }

    private fun isPluginDeclaration(callExpr: KtCallExpression): Boolean {
        val callee = callExpr.calleeExpression?.text
        return callee == "id" || callee == "kotlin"
    }

    private fun extractPluginInfo(callExpr: KtCallExpression): PluginInfo? {
        val text = callExpr.text

        // 格式1: id("plugin.id") version "version"
        val pluginPattern = Regex("""id\s*\(\s*["']([^"']+)["']\s*\)\s+version\s+["']([^"']+)["']""")
        val pluginMatch = pluginPattern.find(text)
        if (pluginMatch != null) {
            return PluginInfo(
                id = pluginMatch.groupValues[1],
                version = pluginMatch.groupValues[2],
                isKotlinPlugin = false,
                element = callExpr
            )
        }

        // 格式2: kotlin("jvm") version "1.9.10"
        val kotlinPattern = Regex("""kotlin\s*\(\s*["']([^"']+)["']\s*\)\s+version\s+["']([^"']+)["']""")
        val kotlinMatch = kotlinPattern.find(text)
        if (kotlinMatch != null) {
            return PluginInfo(
                id = "org.jetbrains.kotlin.${kotlinMatch.groupValues[1]}",
                version = kotlinMatch.groupValues[2],
                isKotlinPlugin = true,
                element = callExpr
            )
        }

        return null
    }

    private data class PluginInfo(
        val id: String,
        val version: String,
        val isKotlinPlugin: Boolean,
        val element: PsiElement
    )
}
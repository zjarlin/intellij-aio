package site.addzero.gradle.buddy.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
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
class GradleKtsPluginToTomlIntention : PsiElementBaseIntentionAction(), IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

    override fun getFamilyName(): String = "Gradle buddy"

    override fun getText(): String = "Convert plugin declaration to TOML format"

    override fun startInWriteAction(): Boolean = true

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html("Converts the Gradle plugin declaration to TOML format.")
    }

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val file = element.containingFile ?: return false
        val fileName = file.name

        return when {
            fileName.endsWith(".gradle.kts") -> detectGradleKtsPlugin(element) != null
            else -> false
        }
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val file = element.containingFile ?: return

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

        // 检查下一行是否为空，如果不是则先换行
        val insertText = if (lineEnd < nextLineStart && document.getText(TextRange(lineEnd, nextLineStart)).trim().isEmpty()) {
            "\n// TOML format:\n// $tomlFormat"
        } else {
            "\n\n// TOML format:\n// $tomlFormat"
        }

        document.insertString(lineEnd, insertText)
    }

    private fun generateTomlFormat(info: PluginInfo): String {
        val pluginId = info.pluginId
        val version = info.version
        val alias = info.alias

        return when {
            alias.isNotEmpty() -> "[plugins.$alias]\nid = \"$pluginId\"\nversion = \"$version\""
            else -> "[plugins]\n${pluginId.replace('-', '.').replace('.', '-')} = { id = \"$pluginId\", version = \"$version\" }"
        }
    }

    private fun detectGradleKtsPlugin(element: PsiElement): PluginInfo? {
        // 查找包含当前元素的 plugins 块
        var current: PsiElement? = element
        while (current != null) {
            // 检查是否在 plugins 块内
            if (isInPluginsBlock(current)) {
                // 查找具体的插件声明
                return findPluginDeclaration(element)
            }
            current = current.parent
        }

        return null
    }

    private fun isInPluginsBlock(element: PsiElement): Boolean {
        return element.text.contains("plugins") || element.parent?.text?.contains("plugins") == true
    }

    private fun findPluginDeclaration(element: PsiElement): PluginInfo? {
        // 向上查找 KtCallExpression
        var current: PsiElement? = element
        while (current != null) {
            if (current is KtCallExpression) {
                // 检查是否是 id("plugin.id") 形式的调用
                val callee = current.calleeExpression?.text
                if (callee == "id") {
                    val firstArgument = current.valueArguments.firstOrNull()
                    val pluginId = extractStringLiteral(firstArgument?.getArgumentExpression())

                    if (pluginId != null) {
                        // 查找版本声明
                        val version = findVersionForPlugin(current)
                        val alias = findAliasForPlugin(current)

                        return PluginInfo(
                            pluginId = pluginId,
                            version = version ?: "\"version\"",
                            alias = alias,
                            element = current
                        )
                    }
                }

                // 检查是否是 kotlin("jvm") 形式的调用
                if (callee in listOf("kotlin", "java", "application")) {
                    val version = findVersionForPlugin(current)
                    val alias = findAliasForPlugin(current)

                    return PluginInfo(
                        pluginId = callee ?: "",
                        version = version ?: "\"version\"",
                        alias = alias,
                        element = current
                    )
                }
            }
            current = current.parent
        }

        // 如果没有找到 call expression，尝试从文本中解析
        val lineText = IntentionUtils.getLineText(element)

        // 格式1: id("plugin.id") version "version"
        val idVersionPattern = Regex("""id\s*\(\s*["']([^"']+)["']\s*\)\s+version\s+["']([^"']+)["']""")
        val idVersionMatch = idVersionPattern.find(lineText)
        if (idVersionMatch != null) {
            return PluginInfo(
                pluginId = idVersionMatch.groupValues[1],
                version = idVersionMatch.groupValues[2],
                alias = "",
                element = element
            )
        }

        // 格式2: kotlin("jvm") version "1.9.10"
        val namedVersionPattern = Regex("""(\w+)\s*\(\s*["']([^"']+)["']\s*\)\s+version\s+["']([^"']+)["']""")
        val namedVersionMatch = namedVersionPattern.find(lineText)
        if (namedVersionMatch != null) {
            return PluginInfo(
                pluginId = namedVersionMatch.groupValues[1],
                version = namedVersionMatch.groupValues[3],
                alias = "",
                element = element
            )
        }

        // 格式3: alias("my-alias") version "1.0.0" applied false
        val aliasPattern = Regex("""alias\s*\(\s*["']([^"']+)["']\s*\)\s+version\s+["']([^"']+)["']""")
        val aliasMatch = aliasPattern.find(lineText)
        if (aliasMatch != null) {
            return PluginInfo(
                pluginId = "",
                version = aliasMatch.groupValues[2],
                alias = aliasMatch.groupValues[1],
                element = element
            )
        }

        return null
    }

    private fun findVersionForPlugin(pluginCall: KtCallExpression): String? {
        // 在同一行或下一个兄弟节点中查找 version 调用
        var next = pluginCall.nextSibling
        val document = pluginCall.containingFile.viewProvider.document!!
        val currentLine = IntentionUtils.getLineNumber(document, pluginCall.textOffset)

        while (next != null && IntentionUtils.getLineNumber(document, next.textOffset) <= currentLine + 1) {
            if (next is KtCallExpression && next.calleeExpression?.text == "version") {
                return extractStringLiteral(next.valueArguments.firstOrNull()?.getArgumentExpression())
            }
            next = next.nextSibling
        }

        return null
    }

    private fun findAliasForPlugin(pluginCall: KtCallExpression): String {
        // 检查是否有 alias 声明
        var prev = pluginCall.prevSibling
        while (prev != null) {
            val text = prev.text.trim()
            if (text.startsWith("alias(")) {
                val aliasPattern = Regex("""alias\s*\(\s*["']([^"']+)["']\s*\)""")
                return aliasPattern.find(text)?.groupValues?.get(1) ?: ""
            }
            prev = prev.prevSibling
        }
        return ""
    }

    private fun extractStringLiteral(element: PsiElement?): String? {
        if (element is KtStringTemplateExpression) {
            return element.text.trim('"')
        }
        return null
    }

    
    private data class PluginInfo(
        val pluginId: String,
        val version: String,
        val alias: String,
        val element: PsiElement
    )
}
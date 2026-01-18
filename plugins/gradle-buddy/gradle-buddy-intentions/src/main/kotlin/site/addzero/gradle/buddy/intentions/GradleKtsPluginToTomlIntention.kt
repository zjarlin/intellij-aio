package site.addzero.gradle.buddy.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * 将 Gradle 插件转换为 TOML 格式的意图操作
 *
 * 此意图操作能够将 Gradle 插件声明从硬编码格式
 * 转换为 build.gradle.kts 文件中的 TOML 版本目录格式。
 *
 * 优先级：高 - 在插件声明时优先显示此意图操作
 *
 * @description 将 Gradle 插件声明转换为 TOML [plugins] 节格式
 */
class GradleKtsPluginToTomlIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

    override fun getFamilyName(): String = "Gradle Buddy"

    override fun getText(): String = "Convert plugins to version catlog (TOML)"

    override fun startInWriteAction(): Boolean = true

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html("Convert plugin declarations to use version directory references.")
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (!file.name.endsWith(".gradle.kts")) return false

        val offset = editor?.caretModel?.offset ?: 0
        val element = file.findElementAt(offset)
        return element != null && findPluginDeclaration(element) != null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        if (!file.name.endsWith(".gradle.kts")) return

        val offset = editor?.caretModel?.offset ?: 0
        val element = file.findElementAt(offset) ?: return

        val pluginInfo = findPluginDeclaration(element) ?: return
        WriteCommandAction.runWriteCommandAction(project) {
            convertToVersionCatalog(file, pluginInfo)
        }
    }

    // 查找插件声明
    private fun findPluginDeclaration(element: PsiElement): PluginInfo? {
        // 向上查找 KtCallExpression
        val callExpr = element.parentOfType<KtCallExpression>(true) ?: return null

        // 检查是否在 plugins 块中
        val isInPluginsBlock = isInsidePluginsBlock(callExpr)
        if (!isInPluginsBlock) return null

        val callee = callExpr.calleeExpression?.text ?: return null

        // 匹配不同的插件声明格式
        when (callee) {
            "id" -> return extractIdPluginInfo(callExpr)
            "kotlin" -> return extractKotlinPluginInfo(callExpr)
            "alias" -> return extractAliasPluginInfo(callExpr)
        }

        return null
    }

    // 检查是否在 plugins 块中
    private fun isInsidePluginsBlock(element: PsiElement): Boolean {
        var current: PsiElement? = element
        while (current != null) {
            val text = current.text
            if (text.contains("plugins") && text.contains("{")) {
                return true
            }
            current = current.parent
        }
        return false
    }

    // 提取 id 格式的插件信息
    private fun extractIdPluginInfo(callExpr: KtCallExpression): PluginInfo? {
        val text = callExpr.text

        // 匹配: id("plugin.id") version "version"
        val pattern = Regex("""id\s*\(\s*["']([^"']+)["']\s*\)\s+version\s+["']([^"']+)["'].*""")
        val match = pattern.find(text) ?: return null

        return PluginInfo(
            id = match.groupValues[1],
            version = match.groupValues[2],
            displayName = match.groupValues[1],
            originalText = match.value,
            element = callExpr
        )
    }

    // 提取 kotlin 格式的插件信息
    private fun extractKotlinPluginInfo(callExpr: KtCallExpression): PluginInfo? {
        val text = callExpr.text

        // 匹配: kotlin("jvm") version "1.9.10"
        val pattern = Regex("""kotlin\s*\(\s*["']([^"']+)["']\s*\)\s+version\s+["']([^"']+)["'].*""")
        val match = pattern.find(text) ?: return null

        val module = match.groupValues[1]
        return PluginInfo(
            id = "org.jetbrains.kotlin.$module",
            version = match.groupValues[2],
            displayName = "kotlin-$module",
            originalText = match.value,
            element = callExpr
        )
    }

    // 提取 alias 格式的插件信息
    private fun extractAliasPluginInfo(callExpr: KtCallExpression): PluginInfo? {
        // alias 插件已经在使用版本目录，不需要转换
        return null
    }

    // 转换为版本目录格式
    private fun convertToVersionCatalog(file: PsiFile, info: PluginInfo) {
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return

        // 生成版本目录中的名称
        val versionKey = generateVersionKey(info.id)
        val pluginKey = generatePluginKey(info.id)

        // 生成 TOML 格式
        val tomlContent = generateTomlContent(info, versionKey, pluginKey)

        // 生成新的插件声明
        val newDeclaration = "alias(libs.plugins.$pluginKey)"

        // 替换原始声明
        val originalText = info.originalText
        val startOffset = info.element.textOffset
        val endOffset = startOffset + info.element.text.length

        document.replaceString(startOffset, endOffset, newDeclaration)

        // 在文件末尾添加 TOML 配置注释
        val comment = "\n\n// 添加到 gradle/libs.versions.toml:\n$tomlContent"
        document.insertString(document.textLength, comment)
    }

    // 生成版本键名
    private fun generateVersionKey(pluginId: String): String {
        // 将插件ID转换为版本键名
        return pluginId
            .replace(".", "-")
            .replace("org-", "")
            .replace("jetbrains-", "")
            .lowercase()
    }

    // 生成插件键名
    private fun generatePluginKey(pluginId: String): String {
        // 将插件ID转换为插件键名
        return pluginId
            .replace(".", "-")
            .replace("org-", "")
            .replace("jetbrains-", "")
            .lowercase()
    }

    // 生成 TOML 内容
    private fun generateTomlContent(info: PluginInfo, versionKey: String, pluginKey: String): String {
        return """[versions]
 $versionKey = "${info.version}"

[plugins]
 $pluginKey = { id = "${info.id}", version.ref = "$versionKey" }"""
    }

    private data class PluginInfo(
        val id: String,
        val version: String,
        val displayName: String,
        val originalText: String,
        val element: PsiElement
    )
}

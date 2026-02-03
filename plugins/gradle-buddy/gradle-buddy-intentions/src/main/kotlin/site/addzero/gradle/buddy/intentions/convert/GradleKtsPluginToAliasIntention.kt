package site.addzero.gradle.buddy.intentions.convert

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.KtCallExpression
import site.addzero.gradle.buddy.settings.GradleBuddySettingsService
import java.io.File

/**
 * 将 Gradle 插件转换为 catalog alias 形式的意图操作
 *
 * 此意图操作能够将硬编码的 Gradle 插件声明（带有 version）
 * 转换为使用版本目录 alias 的形式，并自动合并到 libs.versions.toml 文件中。
 *
 * 仅对以下形式的插件声明有效：
 * - id("plugin.id") version "version"
 * - kotlin("module") version "version"
 *
 * 不支持预编译脚本中的插件声明（因为预编译脚本不能声明 version）
 *
 * 优先级：高 - 在插件声明时优先显示此意图操作
 *
 * @description 将硬编码插件声明转换为 catalog alias 形式
 */
class GradleKtsPluginToAliasIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

    override fun getFamilyName(): String = "Gradle Buddy"

    override fun getText(): String = "(Gradle Buddy) Convert plugin to catalog alias"

    override fun startInWriteAction(): Boolean = true

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html("将硬编码插件声明转换为使用版本目录 alias 并合并到 libs.versions.toml。")
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        // 支持 build.gradle.kts 和 settings.gradle.kts
        if (!file.name.endsWith(".gradle.kts")) return false

        val offset = editor?.caretModel?.offset ?: 0
        val element = file.findElementAt(offset)
        return element != null && findPluginDeclaration(element) != null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        // 支持 build.gradle.kts 和 settings.gradle.kts
        if (!file.name.endsWith(".gradle.kts")) return

        val offset = editor?.caretModel?.offset ?: 0
        val element = file.findElementAt(offset) ?: return

        val pluginInfo = findPluginDeclaration(element) ?: return
        WriteCommandAction.runWriteCommandAction(project) {
            convertToAlias(project, file, pluginInfo)
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
            "alias" -> return null  // alias 已经在使用版本目录，不需要转换
        }

        return null
    }

    // 检查是否在 plugins 块中
    private fun isInsidePluginsBlock(element: PsiElement): Boolean {
        var current: PsiElement? = element
        while (current != null) {
            val text = current.text
            // 检查是否在 plugins 块中（支持 build.gradle.kts 和 settings.gradle.kts）
            if (text.contains("plugins") && text.contains("{")) {
                return true
            }
            current = current.parent
        }
        return false
    }

    // 提取 id 格式的插件信息
    private fun extractIdPluginInfo(callExpr: KtCallExpression): PluginInfo? {
        // KtCallExpression.text 只包含 id("...") 部分，不包含 version
        // 需要获取整行文本来匹配完整的插件声明
        val lineText = getLineText(callExpr)

        // 匹配: id("plugin.id") version "version"
        // 必须包含 version，否则不显示此意图
        val pattern = Regex("""id\s*\(\s*["']([^"']+)["']\s*\)\s+version\s+["']([^"']+)["']""")
        val match = pattern.find(lineText) ?: return null

        // 找到包含 version 的完整表达式元素
        val fullElement = findFullPluginElement(callExpr) ?: callExpr

        return PluginInfo(
            id = match.groupValues[1],
            version = match.groupValues[2],
            displayName = match.groupValues[1],
            originalText = match.value,
            element = fullElement
        )
    }

    // 提取 kotlin 格式的插件信息
    private fun extractKotlinPluginInfo(callExpr: KtCallExpression): PluginInfo? {
        // KtCallExpression.text 只包含 kotlin("...") 部分，不包含 version
        // 需要获取整行文本来匹配完整的插件声明
        val lineText = getLineText(callExpr)

        // 匹配: kotlin("jvm") version "1.9.10"
        // 必须包含 version，否则不显示此意图
        val pattern = Regex("""kotlin\s*\(\s*["']([^"']+)["']\s*\)\s+version\s+["']([^"']+)["']""")
        val match = pattern.find(lineText) ?: return null

        // 找到包含 version 的完整表达式元素
        val fullElement = findFullPluginElement(callExpr) ?: callExpr

        val module = match.groupValues[1]
        return PluginInfo(
            id = "org.jetbrains.kotlin.$module",
            version = match.groupValues[2],
            displayName = "kotlin-$module",
            originalText = match.value,
            element = fullElement
        )
    }

    // 转换为 alias 形式
    private fun convertToAlias(project: Project, file: PsiFile, info: PluginInfo) {
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return

        // 生成版本目录中的名称（TOML 中用 - 分隔）
        val tomlKey = generateTomlKey(info.id)
        // 版本 key 固定命名：${tomlKey}-version
        val versionKey = "$tomlKey-version"

        // 合并到 libs.versions.toml（幂等操作，返回实际使用的 plugin key）
        val actualTomlKey = mergePluginToVersionCatalog(project, info, tomlKey, versionKey)

        // 生成 Kotlin 访问器名称（用 . 分隔，与 plugin id 一致）
        val accessorKey = tomlKeyToAccessorKey(actualTomlKey)

        // 生成新的插件声明
        val newDeclaration = "alias(libs.plugins.$accessorKey)"

        // 替换原始声明
        val startOffset = info.element.textOffset
        val endOffset = startOffset + info.element.text.length

        document.replaceString(startOffset, endOffset, newDeclaration)

        // 刷新虚拟文件系统
        val catalogPath = GradleBuddySettingsService.getInstance(project).getVersionCatalogPath()
        val basePath = project.basePath ?: return
        val catalogFile = File(basePath, catalogPath)
        LocalFileSystem.getInstance().refreshAndFindFileByPath(catalogFile.absolutePath)
    }

    // 合并插件到版本目录文件（幂等操作）
    // 返回实际使用的 toml key（可能是已存在的）
    private fun mergePluginToVersionCatalog(project: Project, info: PluginInfo, tomlKey: String, versionKey: String): String {
        val catalogPath = GradleBuddySettingsService.getInstance(project).getVersionCatalogPath()
        val basePath = project.basePath ?: return tomlKey
        val catalogFile = File(basePath, catalogPath)

        // 确保目录存在
        val catalogDir = catalogFile.parentFile
        if (!catalogDir.exists()) {
            catalogDir.mkdirs()
        }

        // 读取现有内容
        val existingContent = if (catalogFile.exists()) catalogFile.readText() else ""

        // 检查插件是否已存在（通过 plugin id 查找）
        val existingPluginKey = findExistingPluginKey(existingContent, info.id)
        if (existingPluginKey != null) {
            // 插件已存在，直接返回已有的 key
            return existingPluginKey
        }

        // 检查同名版本 key 是否已存在（只匹配同名的，不匹配值）
        val versionExists = checkVersionKeyExists(existingContent, versionKey)

        // 需要添加新条目
        val newContent = appendToVersionCatalog(existingContent, info, tomlKey, versionKey, !versionExists)
        catalogFile.writeText(newContent)

        return tomlKey
    }

    // 查找已存在的插件 key（通过 plugin id）
    private fun findExistingPluginKey(content: String, pluginId: String): String? {
        val lines = content.lines()
        var inPlugins = false

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed == "[plugins]" -> inPlugins = true
                trimmed.startsWith("[") && trimmed != "[plugins]" -> inPlugins = false
                inPlugins && trimmed.contains("=") -> {
                    // 匹配: key = { id = "plugin.id", ... }
                    val keyMatch = Regex("""^([\w-]+)\s*=""").find(trimmed)
                    val idMatch = Regex("""id\s*=\s*"([^"]+)"""").find(trimmed)
                    if (keyMatch != null && idMatch != null && idMatch.groupValues[1] == pluginId) {
                        return keyMatch.groupValues[1]
                    }
                }
            }
        }
        return null
    }

    // 检查版本 key 是否已存在（通过 key 名称匹配，不是通过值）
    private fun checkVersionKeyExists(content: String, versionKey: String): Boolean {
        val lines = content.lines()
        var inVersions = false

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed == "[versions]" -> inVersions = true
                trimmed.startsWith("[") && trimmed != "[versions]" -> inVersions = false
                inVersions && trimmed.contains("=") -> {
                    val parts = trimmed.split("=", limit = 2)
                    if (parts.size == 2 && parts[0].trim() == versionKey) {
                        return true
                    }
                }
            }
        }
        return false
    }

    // 追加到版本目录文件（保留原有内容、注释、顺序）
    private fun appendToVersionCatalog(
        content: String,
        info: PluginInfo,
        pluginKey: String,
        versionKey: String,
        needAddVersion: Boolean
    ): String {
        val lines = content.lines().toMutableList()

        // 查找 [versions] 和 [plugins] section 的位置
        var versionsEndIndex = -1
        var pluginsEndIndex = -1
        var pluginsSectionIndex = -1
        var versionsSectionIndex = -1
        var currentSection = ""

        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            when {
                trimmed == "[versions]" -> {
                    currentSection = "versions"
                    versionsSectionIndex = index
                }
                trimmed == "[plugins]" -> {
                    currentSection = "plugins"
                    pluginsSectionIndex = index
                }
                trimmed.startsWith("[") -> {
                    // 记录上一个 section 的结束位置
                    if (currentSection == "versions") versionsEndIndex = index
                    if (currentSection == "plugins") pluginsEndIndex = index
                    currentSection = trimmed
                }
            }
        }

        // 如果 section 是文件末尾，设置结束位置
        if (currentSection == "versions") versionsEndIndex = lines.size
        if (currentSection == "plugins") pluginsEndIndex = lines.size

        // 构建新内容
        val result = StringBuilder()
        var addedVersion = false
        var addedPlugin = false

        // 如果文件为空或没有 [versions] section
        if (content.isBlank()) {
            if (needAddVersion) {
                result.appendLine("[versions]")
                result.appendLine("$versionKey = \"${info.version}\"")
                result.appendLine()
            }
            result.appendLine("[plugins]")
            result.appendLine("$pluginKey = { id = \"${info.id}\", version.ref = \"$versionKey\" }")
            return result.toString()
        }

        for ((index, line) in lines.withIndex()) {
            result.appendLine(line)

            // 在 [versions] section 末尾添加版本（如果需要）
            if (needAddVersion && !addedVersion && versionsEndIndex > 0 && index == versionsEndIndex - 1) {
                // 找到最后一个非空行的位置插入
                if (line.trim().isNotEmpty() && !line.trim().startsWith("[")) {
                    result.appendLine("$versionKey = \"${info.version}\"")
                    addedVersion = true
                }
            }

            // 在 [plugins] section 末尾添加插件
            if (!addedPlugin && pluginsEndIndex > 0 && index == pluginsEndIndex - 1) {
                if (line.trim().isNotEmpty() && !line.trim().startsWith("[")) {
                    result.appendLine("$pluginKey = { id = \"${info.id}\", version.ref = \"$versionKey\" }")
                    addedPlugin = true
                }
            }
        }

        // 如果没有 [plugins] section，在文件末尾添加
        if (!addedPlugin) {
            if (pluginsSectionIndex < 0) {
                result.appendLine()
                result.appendLine("[plugins]")
            }
            result.appendLine("$pluginKey = { id = \"${info.id}\", version.ref = \"$versionKey\" }")
        }

        // 如果需要添加版本但还没添加（没有 [versions] section）
        if (needAddVersion && !addedVersion) {
            // 在文件开头插入
            val finalResult = StringBuilder()
            finalResult.appendLine("[versions]")
            finalResult.appendLine("$versionKey = \"${info.version}\"")
            finalResult.appendLine()
            finalResult.append(result)
            return finalResult.toString()
        }

        return result.toString().trimEnd() + "\n"
    }

    // 生成 TOML 中的 key（用 - 分隔）
    private fun generateTomlKey(pluginId: String): String {
        return pluginId
            .replace(".", "-")
            .lowercase()
    }

    // 将 TOML key 转换为 Kotlin 访问器名称
    // site-addzero-gradle-plugin-publish-buddy -> site.addzero.gradle.plugin.publish.buddy
    // Gradle 版本目录的访问器使用 . 分隔，与 plugin id 格式一致
    private fun tomlKeyToAccessorKey(tomlKey: String): String {
        return tomlKey.replace("-", ".")
    }

    private data class PluginInfo(
        val id: String,
        val version: String,
        val displayName: String,
        val originalText: String,
        val element: PsiElement
    )

    // 获取元素所在行的文本
    private fun getLineText(element: PsiElement): String {
        val file = element.containingFile ?: return ""
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return ""
        val offset = element.textOffset
        val lineNumber = document.getLineNumber(offset)
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)
        return document.getText(com.intellij.openapi.util.TextRange(lineStart, lineEnd))
    }

    // 查找包含 version 的完整插件表达式元素
    // id("xxx") version "yyy" 在 PSI 中，id("xxx") 是 KtCallExpression，
    // 而 version "yyy" 是通过中缀调用附加的，需要向上查找
    private fun findFullPluginElement(callExpr: KtCallExpression): PsiElement? {
        var current: PsiElement? = callExpr.parent
        while (current != null) {
            val text = current.text
            // 检查是否包含完整的 version 声明
            if (text.contains("version") && text.contains(callExpr.text)) {
                // 确保不是整个 plugins 块
                if (!text.trimStart().startsWith("plugins")) {
                    return current
                }
            }
            // 如果到达语句级别就停止
            if (current is org.jetbrains.kotlin.psi.KtExpression &&
                current.parent is org.jetbrains.kotlin.psi.KtBlockExpression) {
                return current
            }
            current = current.parent
        }
        return null
    }
}

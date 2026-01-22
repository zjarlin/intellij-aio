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
 * 将 Gradle 插件转换为 TOML 格式的意图操作
 *
 * 此意图操作能够将 Gradle 插件声明从硬编码格式
 * 转换为 build.gradle.kts 文件中的 TOML 版本目录格式，
 * 并自动合并到 libs.versions.toml 文件中。
 *
 * 优先级：高 - 在插件声明时优先显示此意图操作
 *
 * @description 将 Gradle 插件声明转换为 TOML [plugins] 节格式并合并到版本目录
 */
class GradleKtsPluginToTomlIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

    override fun getFamilyName(): String = "Gradle Buddy"

    override fun getText(): String = "(Gradle Buddy) Convert plugin to version catalog format (TOML)"

    override fun startInWriteAction(): Boolean = true

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html("将插件声明转换为使用版本目录引用并合并到 libs.versions.toml。")
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

    // 转换为版本目录格式并合并到 libs.versions.toml
    private fun convertToVersionCatalog(file: PsiFile, info: PluginInfo) {
        val project = file.project
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return

        // 生成版本目录中的名称
        val versionKey = generateVersionKey(info.id)
        val pluginKey = generatePluginKey(info.id)

        // 生成新的插件声明
        val newDeclaration = "alias(libs.plugins.$pluginKey)"

        // 替换原始声明
        val startOffset = info.element.textOffset
        val endOffset = startOffset + info.element.text.length

        WriteCommandAction.runWriteCommandAction(project) {
            document.replaceString(startOffset, endOffset, newDeclaration)

            // 合并到 libs.versions.toml
            mergePluginToVersionCatalog(project, info, versionKey, pluginKey)
        }
    }

    // 合并插件到版本目录文件
    private fun mergePluginToVersionCatalog(project: Project, info: PluginInfo, versionKey: String, pluginKey: String) {
        val catalogPath = GradleBuddySettingsService.getInstance(project).getVersionCatalogPath()
        val basePath = project.basePath ?: return
        val catalogFile = File(basePath, catalogPath)

        // 确保目录存在
        val catalogDir = catalogFile.parentFile
        if (!catalogDir.exists()) {
            catalogDir.mkdirs()
        }

        // 解析现有内容
        val existingContent = if (catalogFile.exists()) {
            parseVersionCatalog(catalogFile.readText())
        } else {
            VersionCatalogContent(versions = mutableMapOf(), plugins = mutableMapOf(), libraries = mutableMapOf())
        }

        // 添加新的版本和插件
        existingContent.versions[versionKey] = info.version
        existingContent.plugins[pluginKey] = PluginEntry(
            id = info.id,
            versionRef = versionKey
        )

        // 写入文件
        writeVersionCatalog(catalogFile, existingContent)

        // 刷新虚拟文件系统
        LocalFileSystem.getInstance().refreshAndFindFileByPath(catalogFile.absolutePath)
    }

    // 解析版本目录文件
    private fun parseVersionCatalog(content: String): VersionCatalogContent {
        val versions = mutableMapOf<String, String>()
        val plugins = mutableMapOf<String, PluginEntry>()
        val libraries = mutableMapOf<String, Any>()

        var inVersions = false
        var inPlugins = false
        var inLibraries = false

        content.lines().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed == "[versions]" -> {
                    inVersions = true; inPlugins = false; inLibraries = false
                }

                trimmed == "[plugins]" -> {
                    inVersions = false; inPlugins = true; inLibraries = false
                }

                trimmed == "[libraries]" -> {
                    inVersions = false; inPlugins = false; inLibraries = true
                }

                trimmed.startsWith("[") -> {
                    inVersions = false; inPlugins = false; inLibraries = false
                }

                inVersions && trimmed.contains("=") -> {
                    val parts = trimmed.split("=", limit = 2)
                    if (parts.size == 2) {
                        val name = parts[0].trim()
                        val version = parts[1].trim().removeSurrounding("\"")
                        versions[name] = version
                    }
                }

                inPlugins && trimmed.contains("=") -> {
                    val idMatch = Regex("""^([\w-]+)\s*=""").find(trimmed)
                    val versionRefMatch = Regex("""version\.ref\s*=\s*"([^"]+)"""").find(trimmed)

                    if (idMatch != null && versionRefMatch != null) {
                        val id = idMatch.groupValues[1]
                        val versionRef = versionRefMatch.groupValues[1]
                        plugins[id] = PluginEntry(id = id, versionRef = versionRef)
                    }
                }

                inLibraries && trimmed.contains("=") -> {
                    val aliasMatch = Regex("""^([\w-]+)\s*=""").find(trimmed)
                    val groupMatch = Regex("""group\s*=\s*"([^"]+)"""").find(trimmed)
                    val nameMatch = Regex("""name\s*=\s*"([^"]+)"""").find(trimmed)
                    val versionRefMatch = Regex("""version\.ref\s*=\s*"([^"]+)"""").find(trimmed)

                    if (aliasMatch != null && groupMatch != null && nameMatch != null) {
                        val alias = aliasMatch.groupValues[1]
                        libraries[alias] = LibraryEntry(
                            alias = alias,
                            groupId = groupMatch.groupValues[1],
                            artifactId = nameMatch.groupValues[1],
                            versionRef = versionRefMatch?.groupValues?.get(1) ?: alias
                        )
                    }
                }
            }
        }

        return VersionCatalogContent(versions, plugins, libraries)
    }

    // 写入版本目录文件
    private fun writeVersionCatalog(file: File, content: VersionCatalogContent) {
        val tomlBuilder = StringBuilder()

        // Versions section
        tomlBuilder.appendLine("[versions]")
        content.versions.toSortedMap().forEach { (name, version) ->
            tomlBuilder.appendLine("$name = \"$version\"")
        }

        // Plugins section (如果存在）
        if (content.plugins.isNotEmpty()) {
            tomlBuilder.appendLine()
            tomlBuilder.appendLine("[plugins]")
            content.plugins.toSortedMap().forEach { (id, entry) ->
                tomlBuilder.append("$id = { id = \"${entry.id}\", version.ref = \"${entry.versionRef}\" }")
            }
        }

        // Libraries section (如果存在）
        if (content.libraries.isNotEmpty()) {
            tomlBuilder.appendLine()
            tomlBuilder.appendLine("[libraries]")
            content.libraries.toSortedMap().forEach { (alias, entry) ->
                val libraryEntry = entry as? LibraryEntry
                if (libraryEntry != null) {
                    tomlBuilder.append("$alias = { group = \"${libraryEntry.groupId}\", name = \"${libraryEntry.artifactId}\", version.ref = \"${libraryEntry.versionRef}\"")

                    if (libraryEntry.classifier != null) {
                        tomlBuilder.appendLine(", classifier = \"${libraryEntry.classifier}\"")
                    }

                    tomlBuilder.appendLine(" }")
                }
            }
        }

        file.writeText(tomlBuilder.toString())
    }

    // 生成版本键名
    private fun generateVersionKey(pluginId: String): String {
        return pluginId
            .replace(".", "-")
            .replace("org-", "")
            .replace("jetbrains-", "")
            .lowercase()
    }

    // 生成插件键名
    private fun generatePluginKey(pluginId: String): String {
        return pluginId
            .replace(".", "-")
            .replace("org-", "")
            .replace("jetbrains-", "")
            .lowercase()
    }

    // 版本目录内容
    private data class VersionCatalogContent(
        val versions: MutableMap<String, String>,
        val plugins: MutableMap<String, PluginEntry>,
        val libraries: MutableMap<String, Any>
    )

    private data class PluginEntry(
        val id: String,
        val versionRef: String
    )

    private data class LibraryEntry(
        val alias: String,
        val groupId: String,
        val artifactId: String,
        val versionRef: String,
        val classifier: String? = null
    )

    private data class PluginInfo(
        val id: String,
        val version: String,
        val displayName: String,
        val originalText: String,
        val element: PsiElement
    )
}

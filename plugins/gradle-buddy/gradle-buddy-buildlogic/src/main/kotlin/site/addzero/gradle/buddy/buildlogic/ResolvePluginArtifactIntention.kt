package site.addzero.gradle.buddy.buildlogic

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
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.KtCallExpression
import site.addzero.gradle.buddy.settings.GradleBuddySettingsService
import java.io.File

/**
 * Intention: 解析插件的 build-logic 实现工件
 *
 * 在 .gradle.kts 的 plugins { } 块中，对 `id("xxx") version "yyy"` 提供 Alt+Enter 操作：
 * 1. 通过 Plugin Marker Artifact 机制反查真实实现工件坐标
 * 2. 将工件写入 libs.versions.toml 的 [libraries] 节
 * 3. 提示用户在 buildSrc/build-logic 中使用 implementation(libs.xxx)
 */
class ResolvePluginArtifactIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

    override fun getFamilyName(): String = "Gradle Buddy"

    override fun getText(): String = "(Gradle Buddy) Resolve plugin artifact for build-logic"

    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html(
            "通过 Plugin Marker Artifact 解析插件的真实实现工件坐标，" +
            "写入 libs.versions.toml，用于 build-logic / buildSrc 中引入。"
        )
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (!file.name.endsWith(".gradle.kts")) return false
        val offset = editor?.caretModel?.offset ?: return false
        val element = file.findElementAt(offset) ?: return false
        return extractPluginInfo(element) != null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        val offset = editor?.caretModel?.offset ?: return
        val element = file.findElementAt(offset) ?: return
        val pluginInfo = extractPluginInfo(element) ?: return

        // 网络请求放后台线程
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Resolving plugin artifact for '${pluginInfo.id}'...",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                // 如果没有 version，先查最新版本
                val version: String
                if (pluginInfo.version != null) {
                    version = pluginInfo.version
                } else {
                    indicator.text = "Querying latest version for '${pluginInfo.id}'..."
                    val latestVersion = PluginMarkerResolver.resolveLatestVersion(pluginInfo.id)
                    if (latestVersion == null) {
                        // 查不到最新版本，让用户手动输入工件坐标或版本号
                        ApplicationManager.getApplication().invokeLater {
                            val input = Messages.showInputDialog(
                                project,
                                "无法自动获取插件 '${pluginInfo.id}' 的最新版本。\n\n" +
                                "请手动输入预编译工件坐标（build-logic script plugin artifact）：\n" +
                                "格式: group:artifact:version\n" +
                                "示例: org.graalvm.buildtools:native-gradle-plugin:0.10.4\n\n" +
                                "也可以只输入版本号，将通过 Plugin Marker 自动解析。",
                                "Input Plugin Artifact",
                                null
                            )
                            if (!input.isNullOrBlank()) {
                                handleManualInput(project, pluginInfo, input.trim())
                            }
                        }
                        return
                    }
                    version = latestVersion
                }

                indicator.text = "Querying Plugin Marker POM..."
                val resolved = PluginMarkerResolver.resolve(pluginInfo.id, version)

                ApplicationManager.getApplication().invokeLater {
                    if (resolved == null) {
                        // Marker 解析失败，也让用户手动输入
                        val input = Messages.showInputDialog(
                            project,
                            "无法通过 Plugin Marker 解析插件 '${pluginInfo.id}:$version' 的实现工件。\n\n" +
                            "可能原因：\n" +
                            "• 该插件未发布 Plugin Marker Artifact\n" +
                            "• 该插件托管在私有仓库\n" +
                            "• 网络连接问题\n\n" +
                            "请手动输入预编译工件坐标：\n" +
                            "格式: group:artifact:version\n" +
                            "示例: org.graalvm.buildtools:native-gradle-plugin:0.10.4",
                            "Input Plugin Artifact",
                            null
                        )
                        if (!input.isNullOrBlank()) {
                            handleManualInput(project, pluginInfo, input.trim())
                        }
                        return@invokeLater
                    }

                    // 写入 TOML 并提示
                    writeToVersionCatalog(project, PluginInfo(pluginInfo.id, version), resolved)
                }
            }
        })
    }

    /**
     * 处理用户手动输入：支持两种格式
     * 1. group:artifact:version — 直接写入 TOML，跳过 marker 解析
     * 2. 纯版本号 — 走 marker 解析流程
     */
    private fun handleManualInput(project: Project, pluginInfo: PluginInfo, input: String) {
        val parts = input.split(":")
        if (parts.size == 3 && parts.all { it.isNotBlank() }) {
            // group:artifact:version 格式，直接写入
            val artifact = PluginMarkerResolver.ResolvedArtifact(
                groupId = parts[0].trim(),
                artifactId = parts[1].trim(),
                version = parts[2].trim()
            )
            ApplicationManager.getApplication().invokeLater {
                writeToVersionCatalog(project, PluginInfo(pluginInfo.id, artifact.version), artifact)
            }
        } else {
            // 当作版本号，走 marker 解析
            val version = input.trim()
            ProgressManager.getInstance().run(object : Task.Backgroundable(
                project,
                "Resolving plugin artifact for '${pluginInfo.id}:$version'...",
                true
            ) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    indicator.text = "Querying Plugin Marker POM..."

                    val resolved = PluginMarkerResolver.resolve(pluginInfo.id, version)

                    ApplicationManager.getApplication().invokeLater {
                        if (resolved == null) {
                            Messages.showWarningDialog(
                                project,
                                "无法解析插件 '${pluginInfo.id}:$version' 的实现工件。",
                                "Plugin Artifact Not Found"
                            )
                            return@invokeLater
                        }
                        writeToVersionCatalog(project, PluginInfo(pluginInfo.id, version), resolved)
                    }
                }
            })
        }
    }

    // ========== 插件声明解析 ==========

    private data class PluginInfo(
        val id: String,
        val version: String? // null = no version specified, need to resolve latest
    )

    /**
     * 从光标位置的 PSI 元素提取 plugin id 和 version
     * 支持格式：
     *   id("org.jetbrains.kotlin.jvm") version "2.0.0"   → 带版本
     *   id("org.jetbrains.kotlin.jvm")                    → 无版本（convention plugin 场景）
     *   kotlin("jvm") version "2.0.0"
     *   kotlin("jvm")
     */
    private fun extractPluginInfo(element: PsiElement): PluginInfo? {
        val callExpr = element.parentOfType<KtCallExpression>(true) ?: return null
        if (!isInsidePluginsBlock(callExpr)) return null

        val text = callExpr.text
        val callee = callExpr.calleeExpression?.text ?: return null

        return when (callee) {
            "id" -> {
                // 先尝试带 version 的格式
                val withVersion = Regex("""id\s*\(\s*["']([^"']+)["']\s*\)\s+version\s+["']([^"']+)["']""")
                withVersion.find(text)?.let { return PluginInfo(it.groupValues[1], it.groupValues[2]) }

                // 再尝试不带 version 的格式
                val withoutVersion = Regex("""id\s*\(\s*["']([^"']+)["']\s*\)""")
                withoutVersion.find(text)?.let { PluginInfo(it.groupValues[1], null) }
            }
            "kotlin" -> {
                val withVersion = Regex("""kotlin\s*\(\s*["']([^"']+)["']\s*\)\s+version\s+["']([^"']+)["']""")
                withVersion.find(text)?.let {
                    return PluginInfo("org.jetbrains.kotlin.${it.groupValues[1]}", it.groupValues[2])
                }

                val withoutVersion = Regex("""kotlin\s*\(\s*["']([^"']+)["']\s*\)""")
                withoutVersion.find(text)?.let {
                    PluginInfo("org.jetbrains.kotlin.${it.groupValues[1]}", null)
                }
            }
            else -> null
        }
    }

    private fun isInsidePluginsBlock(element: PsiElement): Boolean {
        var current: PsiElement? = element
        while (current != null) {
            if (current.text.let { it.contains("plugins") && it.contains("{") }) return true
            current = current.parent
        }
        return false
    }

    // ========== TOML 写入 ==========

    private fun writeToVersionCatalog(
        project: Project,
        pluginInfo: PluginInfo,
        resolved: PluginMarkerResolver.ResolvedArtifact
    ) {
        val catalogPath = GradleBuddySettingsService.getInstance(project).getVersionCatalogPath()
        val basePath = project.basePath ?: return
        val catalogFile = File(basePath, catalogPath)

        // 生成 alias 和 version ref
        val alias = generateAlias(resolved)
        val versionRef = generateVersionRef(resolved)

        WriteCommandAction.runWriteCommandAction(project, "Add plugin artifact to version catalog", null, Runnable {
            val catalogDir = catalogFile.parentFile
            if (!catalogDir.exists()) catalogDir.mkdirs()

            if (catalogFile.exists()) {
                mergeIntoCatalog(catalogFile, alias, versionRef, resolved)
            } else {
                createNewCatalog(catalogFile, alias, versionRef, resolved)
            }

            LocalFileSystem.getInstance().refreshAndFindFileByPath(catalogFile.absolutePath)
        })

        // 显示结果
        val ktsAccessor = alias.replace("-", ".")
        Messages.showInfoMessage(
            project,
            "已解析插件实现工件并写入版本目录：\n\n" +
            "插件 ID: ${pluginInfo.id}\n" +
            "版本: ${pluginInfo.version}\n" +
            "实现工件: ${resolved.coordinate}\n\n" +
            "已添加到 $catalogPath:\n" +
            "  [versions] $versionRef = \"${resolved.version}\"\n" +
            "  [libraries] $alias = { group = \"${resolved.groupId}\", name = \"${resolved.artifactId}\", version.ref = \"$versionRef\" }\n\n" +
            "在 buildSrc / build-logic 中使用：\n" +
            "  implementation(libs.$ktsAccessor)",
            "Plugin Artifact Resolved"
        )
    }

    /**
     * 生成 TOML alias：取 artifactId 的 kebab-case
     * 如 kotlin-gradle-plugin → kotlin-gradle-plugin
     * 如 spring-boot-gradle-plugin → spring-boot-gradle-plugin
     */
    private fun generateAlias(artifact: PluginMarkerResolver.ResolvedArtifact): String {
        return artifact.artifactId
            .replace(".", "-")
            .replace("_", "-")
            .lowercase()
    }

    /**
     * 生成 version ref：基于 artifactId
     */
    private fun generateVersionRef(artifact: PluginMarkerResolver.ResolvedArtifact): String {
        return artifact.artifactId
            .replace(".", "-")
            .replace("_", "-")
            .lowercase()
    }

    /**
     * 合并到已有的 TOML 文件
     */
    private fun mergeIntoCatalog(
        file: File,
        alias: String,
        versionRef: String,
        artifact: PluginMarkerResolver.ResolvedArtifact
    ) {
        val content = file.readText()
        val lines = content.lines().toMutableList()

        // 检查是否已存在
        if (lines.any { it.trimStart().startsWith("$alias ") || it.trimStart().startsWith("$alias=") }) {
            return // 已存在，不重复添加
        }

        // 找到 [versions] 节的末尾，插入 version
        val versionsEnd = findSectionEnd(lines, "[versions]")
        if (versionsEnd >= 0) {
            lines.add(versionsEnd, "$versionRef = \"${artifact.version}\"")
        } else {
            // 没有 [versions] 节，在文件开头添加
            lines.add(0, "[versions]")
            lines.add(1, "$versionRef = \"${artifact.version}\"")
            lines.add(2, "")
        }

        // 找到 [libraries] 节的末尾，插入 library
        val librariesEnd = findSectionEnd(lines, "[libraries]")
        val libraryLine = "$alias = { group = \"${artifact.groupId}\", name = \"${artifact.artifactId}\", version.ref = \"$versionRef\" }"
        if (librariesEnd >= 0) {
            lines.add(librariesEnd, libraryLine)
        } else {
            // 没有 [libraries] 节，在文件末尾添加
            lines.add("")
            lines.add("[libraries]")
            lines.add(libraryLine)
        }

        file.writeText(lines.joinToString("\n"))
    }

    /**
     * 创建新的 TOML 文件
     */
    private fun createNewCatalog(
        file: File,
        alias: String,
        versionRef: String,
        artifact: PluginMarkerResolver.ResolvedArtifact
    ) {
        val content = buildString {
            appendLine("[versions]")
            appendLine("$versionRef = \"${artifact.version}\"")
            appendLine()
            appendLine("[libraries]")
            appendLine("$alias = { group = \"${artifact.groupId}\", name = \"${artifact.artifactId}\", version.ref = \"$versionRef\" }")
        }
        file.writeText(content)
    }

    /**
     * 找到 TOML section 的末尾行号（下一个 section 之前，或文件末尾）
     */
    private fun findSectionEnd(lines: List<String>, sectionHeader: String): Int {
        val sectionStart = lines.indexOfFirst { it.trim() == sectionHeader }
        if (sectionStart < 0) return -1

        for (i in (sectionStart + 1) until lines.size) {
            val trimmed = lines[i].trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                // 回退到上一个非空行之后
                var insertAt = i
                while (insertAt > sectionStart + 1 && lines[insertAt - 1].isBlank()) {
                    insertAt--
                }
                return insertAt
            }
        }
        return lines.size
    }
}

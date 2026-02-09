package site.addzero.gradle.buddy.buildlogic

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import site.addzero.gradle.buddy.settings.GradleBuddySettingsService
import java.io.File

/**
 * 批量操作：扫描项目中所有 .gradle.kts 的 plugins { } 块，
 * 解析每个插件的实现工件，写入 libs.versions.toml 的 [libraries] 节。
 *
 * 用于一键生成 build-logic 所需的所有插件依赖。
 */
class ResolveAllPluginArtifactsAction : AnAction(
    "Resolve All Plugin Artifacts for Build-Logic",
    "Scan all plugins in .gradle.kts files and resolve their implementation artifacts to version catalog",
    null
) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Resolving Plugin Artifacts",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                indicator.text = "Scanning .gradle.kts files..."
                indicator.fraction = 0.0

                // 1. 扫描所有 .gradle.kts 文件中的 plugin 声明
                val plugins = scanAllPlugins(project)
                if (plugins.isEmpty()) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage(
                            project,
                            "未在 .gradle.kts 文件中找到带 version 的插件声明。",
                            "No Plugins Found"
                        )
                    }
                    return
                }

                // 去重
                val uniquePlugins = plugins.distinctBy { "${it.id}:${it.version}" }

                indicator.text = "Resolving ${uniquePlugins.size} plugins..."
                indicator.fraction = 0.2

                // 2. 逐个解析
                val resolved = mutableListOf<Pair<PluginDeclaration, PluginMarkerResolver.ResolvedArtifact>>()
                val failed = mutableListOf<PluginDeclaration>()

                uniquePlugins.forEachIndexed { index, plugin ->
                    if (indicator.isCanceled) return

                    indicator.text2 = "${plugin.id}:${plugin.version}"
                    indicator.fraction = 0.2 + 0.7 * (index.toDouble() / uniquePlugins.size)

                    val result = PluginMarkerResolver.resolve(plugin.id, plugin.version)
                    if (result != null) {
                        resolved.add(plugin to result)
                    } else {
                        failed.add(plugin)
                    }
                }

                if (resolved.isEmpty()) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showWarningDialog(
                            project,
                            "找到 ${uniquePlugins.size} 个插件，但均无法解析实现工件。",
                            "Resolution Failed"
                        )
                    }
                    return
                }

                indicator.text = "Writing to version catalog..."
                indicator.fraction = 0.9

                // 3. 批量写入 TOML
                ApplicationManager.getApplication().invokeLater {
                    batchWriteToVersionCatalog(project, resolved)
                    showResult(project, resolved, failed)
                }
            }
        })
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    // ========== 扫描 ==========

    data class PluginDeclaration(
        val id: String,
        val version: String,
        val sourceFile: String
    )

    private fun scanAllPlugins(project: Project): List<PluginDeclaration> {
        val basePath = project.basePath ?: return emptyList()
        val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return emptyList()

        val plugins = mutableListOf<PluginDeclaration>()
        val ktsFiles = mutableListOf<VirtualFile>()

        VfsUtil.iterateChildrenRecursively(baseDir, { file ->
            !file.name.startsWith(".") && file.name != "build" && file.name != "node_modules"
        }) { file ->
            if (file.name.endsWith(".gradle.kts") && !file.path.contains("/build/")) {
                ktsFiles.add(file)
            }
            true
        }

        val idPattern = Regex("""id\s*\(\s*["']([^"']+)["']\s*\)\s+version\s+["']([^"']+)["']""")
        val kotlinPattern = Regex("""kotlin\s*\(\s*["']([^"']+)["']\s*\)\s+version\s+["']([^"']+)["']""")

        for (file in ktsFiles) {
            val content = String(file.contentsToByteArray())

            // 只在 plugins { } 块内匹配
            val pluginsBlockPattern = Regex("""plugins\s*\{([^}]*)\}""", RegexOption.DOT_MATCHES_ALL)
            for (block in pluginsBlockPattern.findAll(content)) {
                val blockContent = block.groupValues[1]

                for (match in idPattern.findAll(blockContent)) {
                    plugins.add(PluginDeclaration(
                        id = match.groupValues[1],
                        version = match.groupValues[2],
                        sourceFile = file.path
                    ))
                }

                for (match in kotlinPattern.findAll(blockContent)) {
                    plugins.add(PluginDeclaration(
                        id = "org.jetbrains.kotlin.${match.groupValues[1]}",
                        version = match.groupValues[2],
                        sourceFile = file.path
                    ))
                }
            }
        }

        return plugins
    }

    // ========== 批量写入 ==========

    private fun batchWriteToVersionCatalog(
        project: Project,
        resolved: List<Pair<PluginDeclaration, PluginMarkerResolver.ResolvedArtifact>>
    ) {
        val catalogPath = GradleBuddySettingsService.getInstance(project).getVersionCatalogPath()
        val basePath = project.basePath ?: return
        val catalogFile = File(basePath, catalogPath)

        WriteCommandAction.runWriteCommandAction(project, "Add plugin artifacts to version catalog", null, Runnable {
            val catalogDir = catalogFile.parentFile
            if (!catalogDir.exists()) catalogDir.mkdirs()

            val existingContent = if (catalogFile.exists()) catalogFile.readText() else ""
            val lines = if (existingContent.isNotEmpty()) {
                existingContent.lines().toMutableList()
            } else {
                mutableListOf("[versions]", "", "[libraries]")
            }

            for ((_, artifact) in resolved) {
                val alias = artifact.artifactId.replace(".", "-").replace("_", "-").lowercase()
                val versionRef = alias

                // 跳过已存在的
                if (lines.any { it.trimStart().startsWith("$alias ") || it.trimStart().startsWith("$alias=") }) {
                    continue
                }

                // 插入 version
                val versionsEnd = findSectionEnd(lines, "[versions]")
                if (versionsEnd >= 0) {
                    lines.add(versionsEnd, "$versionRef = \"${artifact.version}\"")
                }

                // 插入 library
                val librariesEnd = findSectionEnd(lines, "[libraries]")
                val libLine = "$alias = { group = \"${artifact.groupId}\", name = \"${artifact.artifactId}\", version.ref = \"$versionRef\" }"
                if (librariesEnd >= 0) {
                    lines.add(librariesEnd, libLine)
                } else {
                    lines.add("")
                    lines.add("[libraries]")
                    lines.add(libLine)
                }
            }

            catalogFile.writeText(lines.joinToString("\n"))
            LocalFileSystem.getInstance().refreshAndFindFileByPath(catalogFile.absolutePath)
        })
    }

    private fun findSectionEnd(lines: List<String>, sectionHeader: String): Int {
        val sectionStart = lines.indexOfFirst { it.trim() == sectionHeader }
        if (sectionStart < 0) return -1

        for (i in (sectionStart + 1) until lines.size) {
            val trimmed = lines[i].trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                var insertAt = i
                while (insertAt > sectionStart + 1 && lines[insertAt - 1].isBlank()) {
                    insertAt--
                }
                return insertAt
            }
        }
        return lines.size
    }

    // ========== 结果展示 ==========

    private fun showResult(
        project: Project,
        resolved: List<Pair<PluginDeclaration, PluginMarkerResolver.ResolvedArtifact>>,
        failed: List<PluginDeclaration>
    ) {
        val catalogPath = GradleBuddySettingsService.getInstance(project).getVersionCatalogPath()
        val message = buildString {
            appendLine("解析完成！")
            appendLine()
            appendLine("✅ 成功解析 ${resolved.size} 个插件工件：")
            for ((plugin, artifact) in resolved) {
                appendLine("  ${plugin.id} → ${artifact.coordinate}")
            }

            if (failed.isNotEmpty()) {
                appendLine()
                appendLine("❌ 未能解析 ${failed.size} 个插件：")
                for (plugin in failed) {
                    appendLine("  ${plugin.id}:${plugin.version}")
                }
            }

            appendLine()
            appendLine("已写入 $catalogPath")
            appendLine()
            appendLine("在 buildSrc / build-logic 中使用：")
            appendLine("  dependencies {")
            for ((_, artifact) in resolved) {
                val accessor = artifact.artifactId.replace(".", "-").replace("_", "-").lowercase().replace("-", ".")
                appendLine("    implementation(libs.$accessor)")
            }
            appendLine("  }")
        }

        Messages.showInfoMessage(project, message, "Plugin Artifacts Resolved")
    }
}

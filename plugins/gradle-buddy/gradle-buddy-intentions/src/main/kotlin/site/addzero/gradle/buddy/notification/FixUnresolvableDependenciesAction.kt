package site.addzero.gradle.buddy.notification

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import site.addzero.gradle.buddy.intentions.select.VersionSelectionDialog
import site.addzero.gradle.buddy.settings.GradleBuddySettingsService
import site.addzero.network.call.maven.util.MavenCentralSearchUtil
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private val LOG = Logger.getInstance("GradleBuildErrorListener")

/**
 * Startup activity: 编程式注册 GradleBuildErrorListener。
 * 使用 companion object 标记确保全局只注册一次（application 级别的 listener）。
 */
class GradleBuildErrorListenerRegistrar : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (registered) return
        synchronized(this) {
            if (registered) return
            val listener = GradleBuildErrorListener()
            ExternalSystemProgressNotificationManager.getInstance()
                .addNotificationListener(listener)
            registered = true
            LOG.info("GradleBuildErrorListener registered (triggered by project: ${project.name})")
        }
    }

    companion object {
        @Volatile
        private var registered = false
    }
}


/**
 * 监听 Gradle 构建输出和 Gradle Sync 错误，捕获 "Could not find/resolve group:artifact:version" 错误，
 * 解析 Required by 定位报错模块，弹出通知让用户一键修复。
 *
 * 捕获策略（三管齐下）：
 * A. onTaskOutput — 普通 build task 的 stdout/stderr
 * B. onFailure — exception chain（Gradle Sync 的依赖解析错误主要走这条路）
 * C. onStatusChange — 部分 IDE 版本会通过 status event 传递错误描述
 *
 * 修复策略：
 * 1. 先查 TOML（libs.versions.toml）里是否声明了该依赖 → 改 TOML
 * 2. 否则查报错模块的 build.gradle.kts 里是否有硬编码依赖 → 改 kts
 * 3. 都找不到 → 打开 TOML / kts 让用户手动处理
 */
@Suppress("OVERRIDE_DEPRECATION")
class GradleBuildErrorListener : ExternalSystemTaskNotificationListener {

    private val outputBuffers = ConcurrentHashMap<ExternalSystemTaskId, StringBuilder>()
    /** 防止同一个 task 重复弹通知 */
    private val processedTasks = ConcurrentHashMap.newKeySet<ExternalSystemTaskId>()

    override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
        LOG.info("onTaskOutput [${id.type}] stdOut=$stdOut len=${text.length} snippet=${text.take(200)}")
        outputBuffers.getOrPut(id) { StringBuilder() }.append(text)
    }

    override fun onEnd(id: ExternalSystemTaskId) {
        LOG.info("onEnd [${id.type}]")
        // 如果 onFailure 已经处理过，跳过
        if (processedTasks.remove(id)) {
            outputBuffers.remove(id)
            return
        }
        val buffer = outputBuffers.remove(id)
        if (buffer != null) {
            LOG.info("onEnd processing buffer (${buffer.length} chars)")
            processOutput(id, buffer.toString())
        } else {
            LOG.info("onEnd: no buffer found for task $id")
        }
    }

    override fun onFailure(id: ExternalSystemTaskId, e: Exception) {
        // 收集完整的 exception chain 信息 — Gradle Sync 的依赖解析错误主要在这里
        val fullMessage = buildString {
            var current: Throwable? = e
            val visited = mutableSetOf<Throwable>()
            while (current != null && visited.add(current)) {
                appendLine(current.message ?: "")
                // 也检查 suppressed exceptions（Gradle 有时把多个错误放在 suppressed 里）
                for (suppressed in current.suppressed) {
                    appendLine(suppressed.message ?: "")
                }
                current = current.cause
            }
        }
        LOG.info("onFailure [${id.type}] fullMessage=${fullMessage.take(800)}")

        val buffer = outputBuffers.remove(id)
        val combined = buildString {
            if (buffer != null) append(buffer)
            append("\n")
            append(fullMessage)
        }
        // 标记已处理，防止 onEnd 再次处理
        processedTasks.add(id)
        processOutput(id, combined)
    }

    override fun onCancel(id: ExternalSystemTaskId) {
        outputBuffers.remove(id)
        processedTasks.remove(id)
    }

    override fun onStart(id: ExternalSystemTaskId, workingDir: String?) {
        LOG.info("onStart [${id.type}] workingDir=$workingDir")
    }

    override fun onStatusChange(event: ExternalSystemTaskNotificationEvent) {
        // 部分 IDE 版本通过 status event 传递错误描述
        val desc = event.description ?: return
        if (desc.contains("Could not find") || desc.contains("Could not resolve")
            || desc.contains("was not found in any of the following sources")
            || desc.contains("Plugin [id:")) {
            LOG.info("onStatusChange captured error: ${desc.take(300)}")
            outputBuffers.getOrPut(event.id) { StringBuilder() }.append("\n").append(desc)
        }
    }

    override fun onSuccess(id: ExternalSystemTaskId) {
        LOG.info("onSuccess [${id.type}]")
        // 不要在这里清除 buffer！onEnd 还需要用它来检查是否有 dependency resolution warnings
        // outputBuffers 的清理统一在 onEnd 中完成
        processedTasks.remove(id)
    }

    private fun processOutput(id: ExternalSystemTaskId, output: String) {
        val project = id.findProject() ?: return

        // 处理依赖解析错误
        val unresolved = parseUnresolvedDependencies(output)
        if (unresolved.isNotEmpty()) {
            LOG.info("Found ${unresolved.size} unresolved dependencies (type=${id.type})")
            val unique = unresolved.distinctBy { "${it.groupId}:${it.artifactId}:${it.version}" }
            ApplicationManager.getApplication().invokeLater { showFixNotification(project, unique) }
        }

        // 处理插件未找到错误
        val unresolvedPlugins = parseUnresolvedPlugins(output)
        if (unresolvedPlugins.isNotEmpty()) {
            LOG.info("Found ${unresolvedPlugins.size} unresolved plugins (type=${id.type})")
            val uniquePlugins = unresolvedPlugins.distinctBy { it.pluginId }
            ApplicationManager.getApplication().invokeLater { showPluginFixNotification(project, uniquePlugins) }
        }

        if (unresolved.isEmpty() && unresolvedPlugins.isEmpty()) {
            LOG.info("No unresolved dependencies or plugins found in output (${output.length} chars, type=${id.type})")
        }
    }

    /**
     * 解析后的未解析依赖信息。
     * @param requiredByModules 报错的模块路径列表，如 [":lib:ksp:metadata:controller2api-processor"]
     */
    data class UnresolvedDep(
        val groupId: String,
        val artifactId: String,
        val version: String,
        val requiredByModules: List<String> = emptyList()
    )

    /**
     * 解析 Gradle 输出中的未解析依赖。
     *
     * 支持的格式：
     * - "Could not find site.addzero:ksp-support:2025.09.29."
     * - "Could not resolve site.addzero:ksp-support:2025.09.29."
     * - "Required by: project ':lib:ksp:metadata:controller2api-processor'"
     * - task 路径前缀（有无 > 均可）: ":module:path:taskName: Could not find ..."
     * - exception chain 中的多行格式（Gradle Sync 场景）
     * - "Could not resolve all artifacts for configuration ':module:compileClasspath'"
     */
    private fun parseUnresolvedDependencies(output: String): List<UnresolvedDep> {
        val result = mutableListOf<UnresolvedDep>()
        val lines = output.lines()

        // 匹配 "Could not find/resolve group:artifact:version"
        // 注意：group 可能包含点号（如 org.apache.velocity），所以用 [^:\s]+ 匹配
        val depPattern = Regex("""Could not (?:find|resolve)\s+([^:\s]+):([^:\s]+):([^\s.,;)]+)""")
        // 匹配 "Required by:" 后面的 "project ':xxx'"
        val requiredByPattern = Regex("""Required by:\s*$""", RegexOption.IGNORE_CASE)
        val projectPattern = Regex("""project\s+['"]:?([^'"]+)['"]""")
        // 匹配 task 路径前缀中的模块路径（有无 > 前缀均可）:
        // "> :module:path:taskName: Could not find ..."
        // ":module:path:taskName: Could not find ..."
        val taskPrefixPattern = Regex("""^>?\s*:?((?:[^:>\s]+:)*[^:>\s]+):(\w+):\s*Could not""")
        // 匹配 configuration 路径中的模块: "Could not resolve all artifacts for configuration ':module:xxx'"
        val configPattern = Regex("""configuration\s+['"]:?([^:'"]+(?::[^:'"]+)*):([^'"]+)['"]""")
        // 匹配 onStatusChange 的 "for :module:path:sourceSet" 后缀格式:
        // "Could not resolve group:artifact:version for :lib:ksp:common:ksp-easycode:commonMain"
        val forModulePattern = Regex("""\bfor\s+:((?:[^:\s]+:)*[^:\s]+):(\w+)\s*$""")

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val depMatch = depPattern.find(line)
            if (depMatch != null) {
                val groupId = depMatch.groupValues[1]
                val artifactId = depMatch.groupValues[2]
                val version = depMatch.groupValues[3].trimEnd('.')

                val modules = mutableListOf<String>()

                // 策略1: 从 task 前缀提取模块路径
                val taskMatch = taskPrefixPattern.find(line)
                if (taskMatch != null) {
                    val modulePath = ":" + taskMatch.groupValues[1].trimStart(':')
                    if (modulePath !in modules) modules.add(modulePath)
                }

                // 策略2: 从 configuration 路径提取模块
                val configMatch = configPattern.find(line)
                if (configMatch != null) {
                    val modulePath = ":" + configMatch.groupValues[1].trimStart(':')
                    if (modulePath !in modules) modules.add(modulePath)
                }

                // 策略2.5: 从 "for :module:path:sourceSet" 后缀提取模块路径
                // 例如: "Could not resolve site.addzero:ksp-support:2025.09.29 for :lib:ksp:common:ksp-easycode:commonMain"
                val forMatch = forModulePattern.find(line)
                if (forMatch != null) {
                    // groupValues[1] = "lib:ksp:common:ksp-easycode", groupValues[2] = "commonMain"
                    val modulePath = ":" + forMatch.groupValues[1].trimStart(':')
                    if (modulePath !in modules) modules.add(modulePath)
                }

                // 策略3: 向后扫描 "Required by:" 块
                var j = i + 1
                while (j < lines.size && j <= i + 15) {
                    val nextLine = lines[j].trim()
                    if (nextLine.startsWith("Required by:") || requiredByPattern.containsMatchIn(nextLine)) {
                        // 扫描 Required by 块中的 project 引用
                        var k = j
                        if (nextLine == "Required by:") k = j + 1
                        while (k < lines.size && k <= j + 10) {
                            val reqLine = lines[k].trim()
                            if (reqLine.isBlank() || (reqLine.startsWith(">") && !reqLine.contains("project"))) break
                            val projMatch = projectPattern.find(reqLine)
                            if (projMatch != null) {
                                val modulePath = ":" + projMatch.groupValues[1].trimStart(':')
                                if (modulePath !in modules) modules.add(modulePath)
                            }
                            // 也处理简写格式 "Required by: project ':xxx'"
                            if (reqLine.startsWith("Required by:")) {
                                val inlineMatch = projectPattern.find(reqLine)
                                if (inlineMatch != null) {
                                    val modulePath = ":" + inlineMatch.groupValues[1].trimStart(':')
                                    if (modulePath !in modules) modules.add(modulePath)
                                }
                            }
                            k++
                        }
                        break
                    }
                    // 如果遇到另一个 "Could not" 或空行就停止
                    if (nextLine.contains("Could not find") || nextLine.contains("Could not resolve")) break
                    if (nextLine.isBlank()) {
                        // 空行后可能还有 Required by，再看一行
                        if (j + 1 < lines.size && lines[j + 1].trim().startsWith("Required by:")) {
                            j++
                            continue
                        }
                        break
                    }
                    j++
                }

                result.add(UnresolvedDep(groupId, artifactId, version, modules))
            }
            i++
        }
        return result
    }

    // ── Plugin Not Found 解析 ───────────────────────────────────────────

    /**
     * 未解析的插件信息。
     * @param pluginId 插件 ID，如 "site.addzero.gradle.plugin.java-convention"
     * @param buildFilePath 报错的 build 文件路径（从错误输出中提取）
     */
    data class UnresolvedPlugin(
        val pluginId: String,
        val buildFilePath: String? = null
    )

    /**
     * 解析 Gradle 输出中的 "Plugin was not found" 错误。
     *
     * 支持的格式：
     * - "Plugin [id: 'xxx'] was not found in any of the following sources"
     * - "Plugin [id: 'xxx', version: 'yyy'] was not found"
     * - "Build file '/.../build.gradle.kts' line: N"
     */
    private fun parseUnresolvedPlugins(output: String): List<UnresolvedPlugin> {
        val result = mutableListOf<UnresolvedPlugin>()
        val lines = output.lines()

        // 匹配 "Plugin [id: 'xxx'] was not found" 或 "Plugin [id: 'xxx', version: 'yyy'] was not found"
        val pluginPattern = Regex("""Plugin \[id:\s*'([^']+)'(?:,\s*version:\s*'[^']*')?\]\s*was not found""")
        // 匹配 "Build file '/path/to/build.gradle.kts' line: N"
        val buildFilePattern = Regex("""Build file '([^']+)'""")

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val pluginMatch = pluginPattern.find(line)
            if (pluginMatch != null) {
                val pluginId = pluginMatch.groupValues[1]

                // 向前扫描查找 Build file 路径（通常在前几行）
                var buildFilePath: String? = null
                for (j in maxOf(0, i - 5)..i) {
                    val buildFileMatch = buildFilePattern.find(lines[j])
                    if (buildFileMatch != null) {
                        buildFilePath = buildFileMatch.groupValues[1]
                        break
                    }
                }

                // 也向后扫描（有些格式 build file 在后面）
                if (buildFilePath == null) {
                    for (j in i until minOf(lines.size, i + 5)) {
                        val buildFileMatch = buildFilePattern.find(lines[j])
                        if (buildFileMatch != null) {
                            buildFilePath = buildFileMatch.groupValues[1]
                            break
                        }
                    }
                }

                result.add(UnresolvedPlugin(pluginId, buildFilePath))
            }
            i++
        }
        return result
    }

    // ── Plugin Fix Notification ─────────────────────────────────────────

    private fun showPluginFixNotification(project: Project, plugins: List<UnresolvedPlugin>) {
        val summary = plugins.joinToString("\n") { plugin ->
            val file = plugin.buildFilePath?.let { path ->
                val relativePath = project.basePath?.let { base ->
                    path.removePrefix(base.trimEnd('/', '\\') + "/")
                } ?: path
                " (in $relativePath)"
            } ?: ""
            "  • ${plugin.pluginId}$file"
        }
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("GradleBuddy")
            .createNotification(
                "Gradle: ${plugins.size} plugin(s) not found",
                "Plugin not found:\n$summary",
                NotificationType.WARNING
            )

        for (plugin in plugins.take(5)) {
            notification.addAction(object : AnAction("Fix ${plugin.pluginId.substringAfterLast('.')}") {
                override fun actionPerformed(e: AnActionEvent) {
                    fixPluginReference(project, plugin)
                }
            })
        }

        // 添加导航到报错文件的按钮
        val pluginsWithFile = plugins.filter { it.buildFilePath != null }
        if (pluginsWithFile.isNotEmpty()) {
            notification.addAction(object : AnAction("Open Build File") {
                override fun actionPerformed(e: AnActionEvent) {
                    navigateToPluginDeclaration(project, pluginsWithFile.first())
                }
            })
        }

        notification.notify(project)
    }

    /**
     * 修复插件引用：使用 PluginIdScanner 查找正确的全限定 ID 并替换。
     */
    private fun fixPluginReference(project: Project, plugin: UnresolvedPlugin) {
        val scanner = site.addzero.idfixer.PluginIdScanner(project)
        val allPluginInfos = scanner.scanProjectGradleScripts()

        // 提取核心 ID 和关键词用于匹配
        val pluginId = plugin.pluginId
        val coreId = if (pluginId.contains('.')) pluginId.substringAfterLast('.') else pluginId
        val keywords = coreId.split('-', '_', '.').map { it.trim() }.filter { it.isNotBlank() }.distinct()

        // 计算匹配分数
        val candidates = allPluginInfos.mapNotNull { info ->
            if (info.fullyQualifiedId == pluginId) return@mapNotNull null
            val score = calculatePluginMatchScore(coreId, keywords, info)
            if (score <= 0) return@mapNotNull null
            info to score
        }.sortedByDescending { it.second }

        if (candidates.isEmpty()) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("GradleBuddy")
                .createNotification(
                    "No matching build-logic plugin found for '${plugin.pluginId}'",
                    "Try checking your build-logic directory or plugin repositories.",
                    NotificationType.WARNING
                )
                .notify(project)
            return
        }

        if (candidates.size == 1) {
            applyPluginFix(project, plugin, candidates.first().first)
        } else {
            // 多个候选项，显示选择弹窗
            val items = candidates.map { it.first }
            val popup = com.intellij.openapi.ui.popup.JBPopupFactory.getInstance()
                .createPopupChooserBuilder(items)
                .setTitle("Select correct plugin ID for '${plugin.pluginId}'")
                .setRenderer(com.intellij.ui.SimpleListCellRenderer.create("") { info ->
                    val relativePath = project.basePath?.let { base ->
                        info.file.path.removePrefix(base.trimEnd('/', '\\') + "/")
                    } ?: info.file.path
                    "${info.fullyQualifiedId}  ($relativePath)"
                })
                .setItemChosenCallback { chosen ->
                    applyPluginFix(project, plugin, chosen)
                }
                .createPopup()
            popup.showInFocusCenter()
        }
    }

    /**
     * 计算插件匹配分数（与 FixPluginIdIntention 中的逻辑一致）
     */
    private fun calculatePluginMatchScore(
        coreId: String,
        keywords: List<String>,
        info: site.addzero.idfixer.PluginIdInfo
    ): Int {
        val coreLower = coreId.lowercase()
        val shortLower = info.shortId.lowercase()
        val fullLower = info.fullyQualifiedId.lowercase()
        val nameLower = info.file.name.lowercase()

        val normalizedKeywords = keywords.map { it.lowercase() }.distinct()
        val weakKeywords = setOf("convention", "conventions", "plugin", "plugins", "gradle", "build", "logic", "module", "modules")
        val distinctiveKeywords = normalizedKeywords.filterNot { it in weakKeywords }

        var score = 0

        if (shortLower == coreLower) score += 120
        if (shortLower.contains(coreLower) && coreLower.isNotBlank()) score += 60
        if (fullLower.contains(coreLower) && coreLower.isNotBlank()) score += 40
        if (nameLower.contains(coreLower) && coreLower.isNotBlank()) score += 30

        if (normalizedKeywords.isEmpty()) return score

        if (distinctiveKeywords.isNotEmpty()) {
            val hasDistinctiveMatch = distinctiveKeywords.any { kw ->
                shortLower.contains(kw) || fullLower.contains(kw) || nameLower.contains(kw)
            }
            if (!hasDistinctiveMatch) return 0
        }

        var matched = 0
        for (kw in normalizedKeywords) {
            val kwScore = when {
                shortLower.contains(kw) -> 30
                nameLower.contains(kw) -> 25
                fullLower.contains(kw) -> 20
                else -> 0
            }
            if (kwScore > 0) { matched++; score += kwScore }
        }

        if (matched > 0) {
            val coverage = matched.toDouble() / normalizedKeywords.size
            score += (coverage * 40.0).toInt()
            if (matched == normalizedKeywords.size) score += 30
        }

        return score
    }

    /**
     * 应用插件 ID 修复：在报错的 build 文件中替换插件 ID。
     */
    private fun applyPluginFix(
        project: Project,
        plugin: UnresolvedPlugin,
        pluginInfo: site.addzero.idfixer.PluginIdInfo
    ) {
        val engine = site.addzero.idfixer.IdReplacementEngine(
            project,
            mapOf(plugin.pluginId to pluginInfo)
        )

        // 如果有报错文件路径，优先在该文件中修复
        val scope = if (plugin.buildFilePath != null) {
            val vf = LocalFileSystem.getInstance().findFileByPath(plugin.buildFilePath)
            if (vf != null) {
                com.intellij.psi.search.GlobalSearchScope.fileScope(
                    PsiManager.getInstance(project).findFile(vf) ?: return
                )
            } else {
                com.intellij.psi.search.GlobalSearchScope.projectScope(project)
            }
        } else {
            com.intellij.psi.search.GlobalSearchScope.projectScope(project)
        }

        val candidates = engine.findReplacementCandidates(scope)
        if (candidates.isEmpty()) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("GradleBuddy")
                .createNotification(
                    "No occurrences of '${plugin.pluginId}' found to fix",
                    NotificationType.INFORMATION
                )
                .notify(project)
            return
        }

        val result = WriteCommandAction.runWriteCommandAction<site.addzero.idfixer.ReplacementResult>(project) {
            engine.applyReplacements(candidates)
        }

        if (result.isSuccessful()) {
            val message = "Fixed ${result.replacementsMade} occurrence(s) of '${plugin.pluginId}' → '${pluginInfo.fullyQualifiedId}'"
            NotificationGroupManager.getInstance()
                .getNotificationGroup("GradleBuddy")
                .createNotification(message, NotificationType.INFORMATION)
                .notify(project)
        } else {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("GradleBuddy")
                .createNotification(
                    "Failed to fix plugin ID: ${result.errors.firstOrNull() ?: "Unknown error"}",
                    NotificationType.ERROR
                )
                .notify(project)
        }
    }

    /**
     * 导航到报错的 build 文件中的插件声明位置。
     */
    private fun navigateToPluginDeclaration(project: Project, plugin: UnresolvedPlugin) {
        val buildFilePath = plugin.buildFilePath ?: return
        val vf = LocalFileSystem.getInstance().findFileByPath(buildFilePath) ?: run {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("GradleBuddy")
                .createNotification("Cannot locate build file: $buildFilePath", NotificationType.WARNING)
                .notify(project)
            return
        }
        val editors = FileEditorManager.getInstance(project).openFile(vf, true)
        // 尝试定位到插件声明行
        val doc = FileDocumentManager.getInstance().getDocument(vf) ?: return
        val text = doc.text
        val idx = text.indexOf("\"${plugin.pluginId}\"").takeIf { it >= 0 }
            ?: text.indexOf("'${plugin.pluginId}'").takeIf { it >= 0 }
            ?: text.indexOf(plugin.pluginId).takeIf { it >= 0 }
        if (idx != null && idx >= 0) {
            val textEditor = editors.filterIsInstance<TextEditor>().firstOrNull() ?: return
            textEditor.editor.caretModel.moveToOffset(idx)
            textEditor.editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
        }
    }

    // ── Notification ────────────────────────────────────────────────────

    private fun showFixNotification(project: Project, deps: List<UnresolvedDep>) {
        val summary = deps.joinToString("\n") { dep ->
            val modules = if (dep.requiredByModules.isNotEmpty()) {
                " (by ${dep.requiredByModules.joinToString(", ")})"
            } else ""
            "  • ${dep.groupId}:${dep.artifactId}:${dep.version}$modules"
        }
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("GradleBuddy")
            .createNotification(
                "Gradle: ${deps.size} unresolvable dependency(ies)",
                "Could not find/resolve:\n$summary",
                NotificationType.WARNING
            )
        for (dep in deps.take(5)) {
            notification.addAction(object : AnAction("Fix ${dep.artifactId}") {
                override fun actionPerformed(e: AnActionEvent) { fixDependency(project, dep) }
            })
        }
        // 如果有模块信息，添加 Navigate 按钮跳转到报错模块的 kts
        val depsWithModules = deps.filter { it.requiredByModules.isNotEmpty() }
        if (depsWithModules.isNotEmpty()) {
            notification.addAction(object : AnAction("Navigate to Module") {
                override fun actionPerformed(e: AnActionEvent) {
                    val dep = depsWithModules.first()
                    navigateToModuleKts(project, dep)
                }
            })
        }
        if (deps.size > 1) {
            notification.addAction(object : AnAction("Fix All") {
                override fun actionPerformed(e: AnActionEvent) {
                    fixAllDependencies(project, deps)
                    notification.expire()
                }
            })
        }
        notification.addAction(object : AnAction("Open TOML") {
            override fun actionPerformed(e: AnActionEvent) { openCatalogFile(project) }
        })
        notification.notify(project)
    }

    private fun openCatalogFile(project: Project) {
        val catalogPath = GradleBuddySettingsService.getInstance(project).getVersionCatalogPath()
        val basePath = project.basePath ?: return
        val tomlFile = LocalFileSystem.getInstance().findFileByIoFile(File(basePath, catalogPath)) ?: return
        FileEditorManager.getInstance(project).openFile(tomlFile, true)
    }

    // ── Navigate to module build.gradle.kts ─────────────────────────────

    /**
     * 根据模块路径找到对应的 build.gradle.kts 并打开，定位到依赖声明行。
     * 模块路径如 ":lib:ksp:metadata:controller2api-processor" → "lib/ksp/metadata/controller2api-processor/build.gradle.kts"
     */
    private fun navigateToModuleKts(project: Project, dep: UnresolvedDep) {
        val basePath = project.basePath ?: return
        val modulePath = dep.requiredByModules.firstOrNull() ?: return
        val ktsFile = findModuleBuildFile(basePath, modulePath)
        if (ktsFile == null) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("GradleBuddy")
                .createNotification(
                    "Cannot locate build file",
                    "Module $modulePath: build.gradle.kts not found",
                    NotificationType.WARNING
                )
                .notify(project)
            return
        }
        val editors = FileEditorManager.getInstance(project).openFile(ktsFile, true)
        // 尝试定位到依赖声明行
        val doc = FileDocumentManager.getInstance().getDocument(ktsFile) ?: return
        val text = doc.text
        val depCoord = "${dep.groupId}:${dep.artifactId}"
        val idx = text.indexOf(depCoord).takeIf { it >= 0 }
            ?: text.indexOf("\"${dep.artifactId}\"").takeIf { it >= 0 }
            ?: text.indexOf(dep.artifactId).takeIf { it >= 0 }
        if (idx != null && idx >= 0) {
            val textEditor = editors.filterIsInstance<TextEditor>().firstOrNull() ?: return
            textEditor.editor.caretModel.moveToOffset(idx)
            textEditor.editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
        }
    }

    /**
     * 将 Gradle 模块路径转换为文件系统路径，查找 build.gradle.kts 或 build.gradle。
     * ":lib:ksp:metadata:controller2api-processor" → "lib/ksp/metadata/controller2api-processor"
     */
    private fun findModuleBuildFile(basePath: String, modulePath: String): VirtualFile? {
        val relativePath = modulePath.trimStart(':').replace(':', '/')
        val lfs = LocalFileSystem.getInstance()
        // 优先 build.gradle.kts
        lfs.findFileByIoFile(File(basePath, "$relativePath/build.gradle.kts"))?.let { return it }
        // 回退 build.gradle
        lfs.findFileByIoFile(File(basePath, "$relativePath/build.gradle"))?.let { return it }
        return null
    }

    // ── Fix single ──────────────────────────────────────────────────────

    private fun fixDependency(project: Project, dep: UnresolvedDep) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Fetching versions for ${dep.groupId}:${dep.artifactId}...", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                val versions = runCatching {
                    MavenCentralSearchUtil.searchAllVersions(dep.groupId, dep.artifactId, 50)
                        .map { it.latestVersion }.distinct().sortedDescending()
                }.getOrElse { emptyList() }

                ApplicationManager.getApplication().invokeLater {
                    if (versions.isEmpty()) {
                        showPrivateDepNotification(project, dep)
                        return@invokeLater
                    }
                    val dialog = VersionSelectionDialog(project, "Select Version — ${dep.groupId}:${dep.artifactId}", versions, dep.version)
                    if (!dialog.showAndGet()) return@invokeLater
                    val newVersion = dialog.getSelectedVersion() ?: return@invokeLater
                    applyFix(project, dep, newVersion)
                }
            }
        })
    }

    private fun showPrivateDepNotification(project: Project, dep: UnresolvedDep) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("GradleBuddy")
            .createNotification(
                "${dep.groupId}:${dep.artifactId} not on Maven Central",
                "This may be a private artifact. Try: ./gradlew publishToMavenLocal in the source project.",
                NotificationType.WARNING
            )
            .addAction(object : AnAction("Open TOML") {
                override fun actionPerformed(e: AnActionEvent) { openCatalogAndLocate(project, dep) }
            })
        // 如果有模块信息，添加跳转到 kts 的按钮
        if (dep.requiredByModules.isNotEmpty()) {
            notification.addAction(object : AnAction("Open Module KTS") {
                override fun actionPerformed(e: AnActionEvent) { navigateToModuleKts(project, dep) }
            })
        }
        notification.notify(project)
    }

    private fun openCatalogAndLocate(project: Project, dep: UnresolvedDep) {
        val catalogPath = GradleBuddySettingsService.getInstance(project).getVersionCatalogPath()
        val basePath = project.basePath ?: return
        val tomlFile = LocalFileSystem.getInstance().findFileByIoFile(File(basePath, catalogPath)) ?: return
        val editors = FileEditorManager.getInstance(project).openFile(tomlFile, true)
        val doc = FileDocumentManager.getInstance().getDocument(tomlFile) ?: return
        val text = doc.text
        val depCoord = "${dep.groupId}:${dep.artifactId}"
        val idx = text.indexOf(depCoord).takeIf { it >= 0 }
            ?: text.indexOf("name = \"${dep.artifactId}\"").takeIf { it >= 0 } ?: return
        val textEditor = editors.filterIsInstance<TextEditor>().firstOrNull() ?: return
        textEditor.editor.caretModel.moveToOffset(idx)
        textEditor.editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
    }


    // ── Fix all ─────────────────────────────────────────────────────────

    private fun fixAllDependencies(project: Project, deps: List<UnresolvedDep>) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Resolving latest versions...", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                val fixes = mutableListOf<Pair<UnresolvedDep, String>>()
                val notFound = mutableListOf<UnresolvedDep>()
                for ((i, dep) in deps.withIndex()) {
                    indicator.fraction = i.toDouble() / deps.size
                    indicator.text2 = "${dep.groupId}:${dep.artifactId}"
                    val latest = runCatching {
                        MavenCentralSearchUtil.getLatestVersion(dep.groupId, dep.artifactId)
                    }.getOrNull()
                    if (latest != null) fixes.add(dep to latest) else notFound.add(dep)
                    if (indicator.isCanceled) return
                }
                ApplicationManager.getApplication().invokeLater {
                    for ((dep, newVersion) in fixes) applyFix(project, dep, newVersion)
                    val msg = buildString {
                        if (fixes.isNotEmpty()) appendLine("Fixed ${fixes.size}: updated to latest from Maven Central.")
                        if (notFound.isNotEmpty()) {
                            appendLine("${notFound.size} not on Maven Central (private?):")
                            for (d in notFound) appendLine("  • ${d.groupId}:${d.artifactId}:${d.version}")
                        }
                    }
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("GradleBuddy")
                        .createNotification("Fix Unresolvable Dependencies", msg.trim(), NotificationType.INFORMATION)
                        .notify(project)
                }
            }
        })
    }

    // ── Apply fix: 智能选择修复目标（TOML 优先，回退到 kts）──────────────

    /**
     * 智能修复策略：
     * 1. 先查 TOML 里是否声明了该依赖 → 改 TOML 版本
     * 2. 否则查报错模块的 build.gradle.kts 里是否有硬编码依赖 → 改 kts 版本
     * 3. 都找不到 → 尝试改 TOML（兜底）
     */
    private fun applyFix(project: Project, dep: UnresolvedDep, newVersion: String) {
        // 策略1: 尝试在 TOML 中修复
        val tomlFile = findCatalogContaining(project, dep)
        if (tomlFile != null && applyVersionFixInToml(project, dep, newVersion, tomlFile)) {
            LOG.info("Fixed ${dep.groupId}:${dep.artifactId} in TOML")
            return
        }

        // 策略2: 尝试在报错模块的 build.gradle.kts 中修复
        if (dep.requiredByModules.isNotEmpty()) {
            val basePath = project.basePath ?: return
            for (modulePath in dep.requiredByModules) {
                val ktsFile = findModuleBuildFile(basePath, modulePath) ?: continue
                if (applyVersionFixInKts(project, dep, newVersion, ktsFile)) {
                    LOG.info("Fixed ${dep.groupId}:${dep.artifactId} in ${ktsFile.name} (module: $modulePath)")
                    return
                }
            }
        }

        // 策略3: 扫描所有 build.gradle.kts 查找该依赖
        val basePath = project.basePath ?: return
        val baseDir = VfsUtil.findFile(File(basePath).toPath(), true) ?: return
        val ktsFiles = mutableListOf<VirtualFile>()
        VfsUtil.processFileRecursivelyWithoutIgnored(baseDir) { vf ->
            if (!vf.isDirectory && vf.name == "build.gradle.kts") ktsFiles.add(vf)
            true
        }
        for (ktsFile in ktsFiles) {
            if (applyVersionFixInKts(project, dep, newVersion, ktsFile)) {
                LOG.info("Fixed ${dep.groupId}:${dep.artifactId} in ${ktsFile.path}")
                return
            }
        }

        LOG.warn("Could not locate ${dep.groupId}:${dep.artifactId}:${dep.version} in any TOML or KTS file")
    }

    // ── Apply fix to TOML ───────────────────────────────────────────────

    private fun applyVersionFixInToml(project: Project, dep: UnresolvedDep, newVersion: String, tomlFile: VirtualFile): Boolean {
        val psiFile = PsiManager.getInstance(project).findFile(tomlFile) ?: return false
        val document = psiFile.viewProvider.document ?: return false
        val text = document.text
        val depCoord = "${dep.groupId}:${dep.artifactId}"
        val lines = text.lines()

        // Check if it uses version.ref
        val versionRef = findVersionRefForDep(lines, depCoord, dep.groupId, dep.artifactId)
        if (versionRef != null) {
            val versionPattern = Regex("""(?m)^\s*${Regex.escape(versionRef)}\s*=\s*["'][^"']*["']""")
            val vMatch = versionPattern.find(text)
            if (vMatch != null) {
                val newLine = vMatch.value.replace(Regex("""["'][^"']*["']$"""), "\"$newVersion\"")
                WriteCommandAction.runWriteCommandAction(project, "Fix Unresolvable Dependency", null, {
                    document.replaceString(vMatch.range.first, vMatch.range.last + 1, newLine)
                    PsiDocumentManager.getInstance(project).commitDocument(document)
                    FileDocumentManager.getInstance().saveDocument(document)
                })
                return true
            } else {
                // version key missing — create it
                WriteCommandAction.runWriteCommandAction(project, "Fix Unresolvable Dependency", null, {
                    val insertOffset = findVersionsSectionEndOffset(document.text)
                    if (insertOffset >= 0) {
                        document.insertString(insertOffset, "$versionRef = \"$newVersion\"\n")
                    } else {
                        document.insertString(0, "[versions]\n$versionRef = \"$newVersion\"\n\n")
                    }
                    PsiDocumentManager.getInstance(project).commitDocument(document)
                    FileDocumentManager.getInstance().saveDocument(document)
                })
                return true
            }
        }

        // Direct version — replace in library line
        var offset = 0
        var inLibraries = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.matches(SECTION_HEADER)) { inLibraries = trimmed.matches(LIBRARIES_HEADER); offset += line.length + 1; continue }
            if (inLibraries && trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                val matchesDep = trimmed.contains(depCoord) ||
                    (trimmed.contains("group = \"${dep.groupId}\"") && trimmed.contains("name = \"${dep.artifactId}\""))
                if (matchesDep && dep.version.isNotBlank() && trimmed.contains(dep.version)) {
                    val newLine = line.replace(dep.version, newVersion)
                    WriteCommandAction.runWriteCommandAction(project, "Fix Unresolvable Dependency", null, {
                        document.replaceString(offset, offset + line.length, newLine)
                        PsiDocumentManager.getInstance(project).commitDocument(document)
                        FileDocumentManager.getInstance().saveDocument(document)
                    })
                    return true
                }
            }
            offset += line.length + 1
        }
        return false
    }

    // ── Apply fix to build.gradle.kts ───────────────────────────────────

    /**
     * 在 build.gradle.kts 中查找并替换硬编码的依赖版本。
     * 支持的格式：
     * - implementation("group:artifact:version")
     * - implementation("group:artifact") version "version"
     * - "group:artifact:version" (任意配置名)
     */
    private fun applyVersionFixInKts(project: Project, dep: UnresolvedDep, newVersion: String, ktsFile: VirtualFile): Boolean {
        val document = FileDocumentManager.getInstance().getDocument(ktsFile) ?: return false
        val text = document.text
        val depCoord = "${dep.groupId}:${dep.artifactId}"

        // 模式1: "group:artifact:oldVersion" — 最常见的硬编码格式
        val fullCoord = "$depCoord:${dep.version}"
        if (text.contains(fullCoord)) {
            val newCoord = "$depCoord:$newVersion"
            WriteCommandAction.runWriteCommandAction(project, "Fix Dependency in KTS", null, {
                val newText = text.replace(fullCoord, newCoord)
                document.setText(newText)
                PsiDocumentManager.getInstance(project).commitDocument(document)
                FileDocumentManager.getInstance().saveDocument(document)
            })
            return true
        }

        // 模式2: version("oldVersion") 或 version "oldVersion" 紧跟在依赖坐标后
        // 例如: implementation("group:artifact") { version { strictly("1.0") } }
        if (text.contains(depCoord) && dep.version.isNotBlank() && text.contains(dep.version)) {
            val lines = text.lines()
            var offset = 0
            for (line in lines) {
                if (line.contains(depCoord) || (line.contains(dep.groupId) && line.contains(dep.artifactId))) {
                    // 在这一行或附近几行查找版本号
                    if (line.contains(dep.version)) {
                        val newLine = line.replace(dep.version, newVersion)
                        WriteCommandAction.runWriteCommandAction(project, "Fix Dependency in KTS", null, {
                            document.replaceString(offset, offset + line.length, newLine)
                            PsiDocumentManager.getInstance(project).commitDocument(document)
                            FileDocumentManager.getInstance().saveDocument(document)
                        })
                        return true
                    }
                }
                offset += line.length + 1
            }
        }

        return false
    }


    // ── Catalog lookup helpers ──────────────────────────────────────────

    private fun findCatalogContaining(project: Project, dep: UnresolvedDep): VirtualFile? {
        val catalogPath = GradleBuddySettingsService.getInstance(project).getVersionCatalogPath()
        val basePath = project.basePath ?: return null
        val configured = LocalFileSystem.getInstance().findFileByIoFile(File(basePath, catalogPath))
        if (configured != null) {
            val text = FileDocumentManager.getInstance().getDocument(configured)?.text
                ?: String(configured.contentsToByteArray())
            val depCoord = "${dep.groupId}:${dep.artifactId}"
            if (text.contains(depCoord) || (text.contains(dep.groupId) && text.contains(dep.artifactId))) return configured
        }
        val baseDir = VfsUtil.findFile(File(basePath).toPath(), true) ?: return configured
        val candidates = mutableListOf<VirtualFile>()
        VfsUtil.processFileRecursivelyWithoutIgnored(baseDir) { vf ->
            if (!vf.isDirectory && vf.name.endsWith(".versions.toml")) candidates.add(vf)
            true
        }
        val depCoord = "${dep.groupId}:${dep.artifactId}"
        for (candidate in candidates) {
            val text = FileDocumentManager.getInstance().getDocument(candidate)?.text
                ?: String(candidate.contentsToByteArray())
            if (text.contains(depCoord) || (text.contains(dep.groupId) && text.contains(dep.artifactId))) return candidate
        }
        return configured
    }

    private fun findVersionRefForDep(lines: List<String>, depCoord: String, groupId: String, artifactId: String): String? {
        var inLibraries = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.matches(SECTION_HEADER)) { inLibraries = trimmed.matches(LIBRARIES_HEADER); continue }
            if (!inLibraries || trimmed.isBlank() || trimmed.startsWith("#")) continue
            if (trimmed.contains("\"$depCoord\"") ||
                (trimmed.contains("group = \"$groupId\"") && trimmed.contains("name = \"$artifactId\""))) {
                return VERSION_REF_PATTERN.find(trimmed)?.groupValues?.get(1)
            }
        }
        return null
    }

    companion object {
        private val SECTION_HEADER = Regex("^\\[.+]\\s*(#.*)?$")
        private val LIBRARIES_HEADER = Regex("^\\[libraries]\\s*(#.*)?$")
        private val VERSION_REF_PATTERN = Regex("""version\.ref\s*=\s*"([^"]+)"""")

        private fun findVersionsSectionEndOffset(text: String): Int {
            val lines = text.split('\n')
            var inVersions = false
            var lastVersionLineEnd = -1
            var offset = 0
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed == "[versions]") { inVersions = true; offset += line.length + 1; continue }
                if (inVersions) {
                    if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                        return if (lastVersionLineEnd >= 0) lastVersionLineEnd else offset
                    }
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) lastVersionLineEnd = offset + line.length + 1
                }
                offset += line.length + 1
            }
            return if (inVersions && lastVersionLineEnd >= 0) lastVersionLineEnd else -1
        }
    }
}

package site.addzero.gradle.sleep

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.MessageBusConnection
import site.addzero.gradle.sleep.loader.OnDemandModuleLoader
import site.addzero.gradle.sleep.settings.ModuleSleepSettingsService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
class GradleModuleSleepService(private val project: Project) : Disposable {

    private val logger = Logger.getInstance(GradleModuleSleepService::class.java)
    private var connection: MessageBusConnection? = null
    private val scheduledExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    @Volatile
    private var featureAvailable: Boolean? = null

    // 跟踪每个模块最后访问时间 (模块路径 -> 时间戳)
    private val moduleLastAccessTime = ConcurrentHashMap<String, Long>()

    // 跟踪打开的文件所属模块
    private val openFileModules = ConcurrentHashMap<String, String>()

    // 当前已加载的模块集合
    private val loadedModules = ConcurrentHashMap.newKeySet<String>()

    // 检查间隔（毫秒）
    private val CHECK_INTERVAL = 30_000L // 30秒

    // 防止频繁同步的debounce
    @Volatile
    private var pendingSync = false
    private val SYNC_DEBOUNCE = 2_000L // 2秒

    fun init() {
        if (!isFeatureAvailable()) {
            logger.info("Gradle Module Sleep disabled: module count below threshold")
            return
        }
        connection = project.messageBus.connect()
        connection?.subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    handleFileOpened(file)
                }

                override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                    handleFileClosed(file)
                }
            }
        )

        // 初始化：扫描当前打开的文件
        initializeFromOpenFiles()

        // 启动定期检查任务
        startPeriodicCheck()

        logger.info("Gradle Module Sleep initialized for project: ${project.name}")
    }

    private fun initializeFromOpenFiles() {
        val openFiles = FileEditorManager.getInstance(project).openFiles
        openFiles.forEach { file ->
            detectModulePath(file)?.let { modulePath ->
                moduleLastAccessTime[modulePath] = System.currentTimeMillis()
                openFileModules[file.path] = modulePath
                loadedModules.add(modulePath)
            }
        }
    }

    private fun handleFileOpened(file: VirtualFile) {
        val modulePath = detectModulePath(file) ?: return

        // 更新模块访问时间
        moduleLastAccessTime[modulePath] = System.currentTimeMillis()
        openFileModules[file.path] = modulePath

        // 检查模块是否需要加载
        if (!loadedModules.contains(modulePath) && modulePath != ":") {
            logger.info("Module opened: $modulePath, scheduling load...")
            loadedModules.add(modulePath)
            scheduleModuleSync()
        }
    }

    private fun handleFileClosed(file: VirtualFile) {
        openFileModules.remove(file.path)
    }

    private fun detectModulePath(file: VirtualFile): String? {
        val projectBasePath = project.basePath ?: return null
        val filePath = file.path

        if (!filePath.startsWith(projectBasePath)) return null

        var currentPath = file.parent
        while (currentPath != null && currentPath.path.startsWith(projectBasePath)) {
            val hasBuildFile = currentPath.findChild("build.gradle.kts") != null ||
                              currentPath.findChild("build.gradle") != null

            if (hasBuildFile) {
                val moduleRelativePath = currentPath.path.removePrefix(projectBasePath).trimStart('/')
                if (shouldExcludeModule(moduleRelativePath)) return null
                return if (moduleRelativePath.isEmpty()) ":"
                       else ":${moduleRelativePath.replace('/', ':')}"
            }
            currentPath = currentPath.parent
        }
        return null
    }

    private fun shouldExcludeModule(relativePath: String): Boolean {
        val excludePatterns = listOf(
            "buildSrc",
            "build-logic",
            "checkouts/build-logic",
        )
        return excludePatterns.any { pattern ->
            relativePath == pattern || relativePath.startsWith("$pattern/") || relativePath.contains("/$pattern")
        }
    }

    private fun scheduleModuleSync() {
        if (pendingSync) return
        pendingSync = true

        scheduledExecutor.schedule({
            pendingSync = false
            syncActiveModules()
        }, SYNC_DEBOUNCE, TimeUnit.MILLISECONDS)
    }

    private fun syncActiveModules() {
        // 检查是否启用自动睡眠，如果未启用则不同步
        if (!isAutoSleepActive()) {
            logger.info("Auto-sleep is disabled, skipping module sync")
            return
        }

        // 清除文件系统中已不存在的旧模块路径（如重命名/删除后的残留）
        purgeStaleModules()

        ApplicationManager.getApplication().invokeLater {
            val settings = ModuleSleepSettingsService.getInstance(project)
            val manualModules = OnDemandModuleLoader.findModulesByFolderNames(project, settings.getManualFolderNames())
            val activeModules = OnDemandModuleLoader.expandModulesWithDependencies(project, loadedModules.toSet() + manualModules)
            if (activeModules.isEmpty()) return@invokeLater

            val success = OnDemandModuleLoader.applyOnDemandLoading(project, activeModules, syncAfter = true)
            if (success) {
                logger.info("Synced ${activeModules.size} active modules")
                showNotification("Modules Loaded", "Loaded modules: ${activeModules.joinToString(", ")}")
            }
        }
    }

    private fun startPeriodicCheck() {
        scheduledExecutor.scheduleWithFixedDelay({
            try {
                checkAndReleaseUnusedModules()
            } catch (e: Exception) {
                logger.warn("Error during module check", e)
            }
        }, CHECK_INTERVAL, CHECK_INTERVAL, TimeUnit.MILLISECONDS)
    }

    private fun checkAndReleaseUnusedModules() {
        // 检查是否启用自动睡眠
        if (!isAutoSleepActive()) return

        // 清除文件系统中已不存在的旧模块路径
        purgeStaleModules()

        val settings = ModuleSleepSettingsService.getInstance(project)
        val idleTimeoutMinutes = settings.getModuleIdleTimeoutMinutes()
        val moduleIdleTimeout = idleTimeoutMinutes * 60 * 1000L

        val now = System.currentTimeMillis()
        val activeModulePaths = openFileModules.values.toSet()
        val manualModules = OnDemandModuleLoader.findModulesByFolderNames(project, settings.getManualFolderNames())
        val protectedModules = OnDemandModuleLoader.expandModulesWithDependencies(project, manualModules)

        // 找出可以释放的模块
        val modulesToRelease = loadedModules.filter { modulePath ->
            if (modulePath == ":") return@filter false
            if (activeModulePaths.contains(modulePath)) return@filter false
            if (protectedModules.contains(modulePath)) return@filter false

            val lastAccess = moduleLastAccessTime[modulePath] ?: 0L
            val idleTime = now - lastAccess
            idleTime > moduleIdleTimeout
        }

        if (modulesToRelease.isNotEmpty()) {
            logger.info("Releasing ${modulesToRelease.size} unused modules: $modulesToRelease")
            modulesToRelease.forEach { modulePath ->
                loadedModules.remove(modulePath)
                moduleLastAccessTime.remove(modulePath)
            }

            // 重新同步只保留活跃模块
            ApplicationManager.getApplication().invokeLater {
                val remainingModules = OnDemandModuleLoader.expandModulesWithDependencies(project, loadedModules.toSet() + protectedModules)
                if (remainingModules.isNotEmpty()) {
                    OnDemandModuleLoader.applyOnDemandLoading(project, remainingModules, syncAfter = true)
                    showNotification("Modules Released", "Released: ${modulesToRelease.joinToString(", ")}")
                }
            }
        }
    }

    /**
     * 清除 loadedModules 中在文件系统上已不存在的模块路径。
     * 典型场景：用户重命名或删除了模块目录，旧路径仍残留在内存缓存中。
     */
    private fun purgeStaleModules() {
        val projectBasePath = project.basePath ?: return
        val stale = loadedModules.filter { modulePath ->
            if (modulePath == ":") return@filter false
            val relativePath = modulePath.removePrefix(":").replace(':', '/')
            val moduleDir = java.io.File(projectBasePath, relativePath)
            // 目录不存在，或者不再包含 build 文件 → 视为过期
            !moduleDir.isDirectory ||
                (!java.io.File(moduleDir, "build.gradle.kts").exists() &&
                 !java.io.File(moduleDir, "build.gradle").exists())
        }
        if (stale.isNotEmpty()) {
            logger.info("Purging ${stale.size} stale module(s) from cache: $stale")
            stale.forEach { modulePath ->
                loadedModules.remove(modulePath)
                moduleLastAccessTime.remove(modulePath)
            }
            // 同步清理 openFileModules 中指向已失效模块的条目
            openFileModules.entries.removeIf { (_, mod) -> stale.contains(mod) }
        }
    }

    fun restoreAllModules() {
        OnDemandModuleLoader.restoreAllModules(project, syncAfter = true)
    }

    fun getLoadedModules(): Set<String> = loadedModules.toSet()

    /**
     * 检查自动睡眠是否生效
     * - 用户明确设置 true/false 时使用用户设置
     * - 用户未设置 (null) 时，根据模块数量自动判断
     */
    fun isAutoSleepActive(): Boolean {
        if (!isFeatureAvailable()) return false
        val settings = ModuleSleepSettingsService.getInstance(project)
        return when (val userSetting = settings.getAutoSleepEnabled()) {
            true -> true
            false -> false
            null -> getModuleCount() >= ModuleSleepSettingsService.LARGE_PROJECT_THRESHOLD
        }
    }

    fun isFeatureAvailable(): Boolean {
        featureAvailable?.let { return it }
        if (isModulesBuddyPluginEnabled()) {
            featureAvailable = false
            logger.info("Gradle Module Sleep disabled: modules-buddy plugin detected in settings.gradle.kts")
            return false
        }
        val available = getModuleCount() >= ModuleSleepSettingsService.LARGE_PROJECT_THRESHOLD
        featureAvailable = available
        return available
    }

    /**
     * 检测项目的 settings.gradle.kts 中是否启用了 modules-buddy 插件。
     * 如果启用了，则 gradle-module-sleep 应自动禁用，避免功能冲突。
     *
     * 通过 GradleSettings 获取所有 linked Gradle project 的真实根路径，
     * 而非依赖 project.basePath（后者在子目录打开项目时不可靠）。
     */
    private fun isModulesBuddyPluginEnabled(): Boolean {
        return try {
            val gradleSettings = org.jetbrains.plugins.gradle.settings.GradleSettings.getInstance(project)
            val rootPaths = gradleSettings.linkedProjectsSettings.map { it.externalProjectPath }
            // 如果没有 linked Gradle project，fallback 到 basePath
            val candidates = rootPaths.ifEmpty { listOfNotNull(project.basePath) }
            candidates.any { rootPath ->
                containsModulesBuddyPlugin(java.io.File(rootPath, "settings.gradle.kts"))
                    || containsModulesBuddyPlugin(java.io.File(rootPath, "settings.gradle"))
            }
        } catch (e: Exception) {
            logger.warn("Failed to detect modules-buddy plugin", e)
            false
        }
    }

    private fun containsModulesBuddyPlugin(settingsFile: java.io.File): Boolean {
        if (!settingsFile.exists()) return false
        return try {
            settingsFile.readText().lines().any { line ->
                val trimmed = line.trim()
                !trimmed.startsWith("//") && trimmed.contains("site.addzero.gradle.plugin.modules-buddy")
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 获取项目模块数量
     */
    fun getModuleCount(): Int {
        return OnDemandModuleLoader.discoverAllModules(project).size
    }

    private fun showNotification(title: String, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("GradleModuleSleep")
            .createNotification(title, content, NotificationType.INFORMATION)
            .notify(project)
    }

    override fun dispose() {
        connection?.disconnect()
        scheduledExecutor.shutdown()
        moduleLastAccessTime.clear()
        openFileModules.clear()
        loadedModules.clear()
    }
}

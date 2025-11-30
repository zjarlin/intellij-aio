package site.addzero.gradle.buddy

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.plugins.gradle.settings.GradleSettings
import site.addzero.gradle.buddy.ondemand.OnDemandModuleLoader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class GradleBuddyPlugin : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.service<GradleBuddyService>().init()
    }
}

@Service(Service.Level.PROJECT)
class GradleBuddyService(private val project: Project) {
    
    private val logger = Logger.getInstance(GradleBuddyService::class.java)
    private var connection: MessageBusConnection? = null
    private val scheduledExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    
    // 跟踪每个模块最后访问时间 (模块路径 -> 时间戳)
    private val moduleLastAccessTime = ConcurrentHashMap<String, Long>()
    
    // 跟踪打开的文件所属模块
    private val openFileModules = ConcurrentHashMap<String, String>()
    
    // 当前已加载的模块集合
    private val loadedModules = ConcurrentHashMap.newKeySet<String>()
    
    // 检查间隔（毫秒）
    private val CHECK_INTERVAL = 30_000L // 30秒
    
    // 模块闲置超时时间（毫秒）- 超过此时间未访问的模块将被释放
    private val MODULE_IDLE_TIMEOUT = 5 * 60 * 1000L // 5分钟
    
    // 防止频繁同步的debounce
    @Volatile
    private var pendingSync = false
    private val SYNC_DEBOUNCE = 2_000L // 2秒
    
    fun init() {
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
        
        logger.info("GradleBuddy initialized for project: ${project.name}")
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
        
        // 检查 Gradle 项目状态
        checkGradleProject()
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
                return if (moduleRelativePath.isEmpty()) ":"
                       else ":${moduleRelativePath.replace('/', ':')}"
            }
            currentPath = currentPath.parent
        }
        return null
    }
    
    private fun checkGradleProject() {
        if (isGradleProject() && !isGradleProjectLoaded()) {
            showGradleLoadIndicator()
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
        ApplicationManager.getApplication().invokeLater {
            val activeModules = loadedModules.toSet()
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
        val now = System.currentTimeMillis()
        val activeModulePaths = openFileModules.values.toSet()
        
        // 找出可以释放的模块
        val modulesToRelease = loadedModules.filter { modulePath ->
            if (modulePath == ":") return@filter false
            if (activeModulePaths.contains(modulePath)) return@filter false
            
            val lastAccess = moduleLastAccessTime[modulePath] ?: 0L
            val idleTime = now - lastAccess
            idleTime > MODULE_IDLE_TIMEOUT
        }
        
        if (modulesToRelease.isNotEmpty()) {
            logger.info("Releasing ${modulesToRelease.size} unused modules: $modulesToRelease")
            modulesToRelease.forEach { modulePath ->
                loadedModules.remove(modulePath)
                moduleLastAccessTime.remove(modulePath)
            }
            
            // 重新同步只保留活跃模块
            ApplicationManager.getApplication().invokeLater {
                val remainingModules = loadedModules.toSet()
                if (remainingModules.isNotEmpty()) {
                    OnDemandModuleLoader.applyOnDemandLoading(project, remainingModules, syncAfter = true)
                    showNotification("Modules Released", "Released: ${modulesToRelease.joinToString(", ")}")
                }
            }
        }
    }
    
    fun restoreAllModules() {
        OnDemandModuleLoader.restoreAllModules(project, syncAfter = true)
    }
    
    fun getLoadedModules(): Set<String> = loadedModules.toSet()
    
    private fun isGradleProject(): Boolean {
        val baseDir = project.guessProjectDir() ?: return false
        return baseDir.findChild("build.gradle") != null || 
               baseDir.findChild("build.gradle.kts") != null ||
               baseDir.findChild("settings.gradle") != null ||
               baseDir.findChild("settings.gradle.kts") != null
    }
    
    private fun isGradleProjectLoaded(): Boolean {
        return GradleSettings.getInstance(project).linkedProjectsSettings.isNotEmpty()
    }
    
    private fun showGradleLoadIndicator() {
        val statusBar = WindowManager.getInstance().getStatusBar(project)
        val widget = statusBar?.getWidget(GradleBuddyWidgetFactory.WIDGET_ID)
        if (widget is GradleBuddyWidget) {
            widget.showIndicator()
        }
    }
    
    private fun showNotification(title: String, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("GradleBuddy")
            .createNotification(title, content, NotificationType.INFORMATION)
            .notify(project)
    }
    
    fun dispose() {
        connection?.disconnect()
        scheduledExecutor.shutdown()
        moduleLastAccessTime.clear()
        openFileModules.clear()
        loadedModules.clear()
    }
}
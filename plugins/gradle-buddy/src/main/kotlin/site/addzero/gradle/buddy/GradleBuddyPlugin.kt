package site.addzero.gradle.buddy

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
import site.addzero.gradle.buddy.module.GradleModuleManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class GradleBuddyPlugin : ProjectActivity {
    override suspend fun execute(project: Project) {
        // 初始化插件
        project.service<GradleBuddyService>().init()
        
        // 注册状态栏指示器
        registerStatusBarWidget(project)
    }
    
    private fun registerStatusBarWidget(project: Project) {
        val statusBar = WindowManager.getInstance().getStatusBar(project) ?: return
        val widget = GradleBuddyWidget(project)
        @Suppress("DEPRECATION")
        statusBar.addWidget(widget)
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
    
    // 检查间隔（毫秒）
    private val CHECK_INTERVAL = 30000L // 30秒
    
    // 模块闲置超时时间（毫秒）- 超过此时间未访问的模块将被释放
    private val MODULE_IDLE_TIMEOUT = 5 * 60 * 1000L // 5分钟
    
    fun init() {
        // 监听文件编辑器事件
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
        
        // 启动定期检查任务
        startPeriodicCheck()
        
        logger.info("GradleBuddy initialized for project: ${project.name}")
    }
    
    private fun handleFileOpened(file: VirtualFile) {
        val modulePath = detectModulePath(file) ?: return
        
        // 更新模块访问时间
        moduleLastAccessTime[modulePath] = System.currentTimeMillis()
        openFileModules[file.path] = modulePath
        
        // 如果模块之前被排除，自动恢复
        val excludedModules = GradleModuleManager.getExcludedModules(project)
        if (excludedModules.any { it.path == modulePath }) {
            logger.info("Auto-including previously excluded module: $modulePath")
            GradleModuleManager.includeModule(project, modulePath)
            GradleModuleManager.triggerGradleSync(project)
        }
        
        // 检查 Gradle 项目状态
        checkGradleProject(file)
    }
    
    private fun handleFileClosed(file: VirtualFile) {
        openFileModules.remove(file.path)
    }
    
    /**
     * 检测文件所属的模块路径
     * 例如：/project/plugins/gradle-buddy/src/main/... -> :plugins:gradle-buddy
     */
    private fun detectModulePath(file: VirtualFile): String? {
        val projectBasePath = project.basePath ?: return null
        val filePath = file.path
        
        if (!filePath.startsWith(projectBasePath)) return null
        
        val relativePath = filePath.removePrefix(projectBasePath).trimStart('/')
        
        // 查找包含 build.gradle 的最近父目录
        var currentPath = file.parent
        while (currentPath != null && currentPath.path.startsWith(projectBasePath)) {
            val hasBuildFile = currentPath.findChild("build.gradle.kts") != null ||
                              currentPath.findChild("build.gradle") != null
            
            if (hasBuildFile) {
                val moduleRelativePath = currentPath.path.removePrefix(projectBasePath).trimStart('/')
                return if (moduleRelativePath.isEmpty()) {
                    ":" // 根项目
                } else {
                    ":" + moduleRelativePath.replace('/', ':')
                }
            }
            currentPath = currentPath.parent
        }
        
        return null
    }
    
    @Suppress("UNUSED_PARAMETER")
    private fun checkGradleProject(file: VirtualFile) {
        if (isGradleProject() && !isGradleProjectLoaded()) {
            showGradleLoadIndicator()
        }
    }
    
    private fun startPeriodicCheck() {
        val task = Runnable {
            try {
                checkAndReleaseUnusedModules()
            } catch (e: Exception) {
                logger.warn("Error during module check", e)
            }
        }
        scheduledExecutor.scheduleWithFixedDelay(task, CHECK_INTERVAL, CHECK_INTERVAL, TimeUnit.MILLISECONDS)
    }
    
    private fun checkAndReleaseUnusedModules() {
        val now = System.currentTimeMillis()
        val activeModules = openFileModules.values.toSet()
        
        // 获取当前 include 的模块
        val includedModules = GradleModuleManager.getIncludedModules(project)
        
        // 找出可以释放的模块
        val modulesToRelease = includedModules.filter { module ->
            // 模块没有打开的文件
            val hasOpenFiles = activeModules.contains(module.path)
            if (hasOpenFiles) return@filter false
            
            // 检查最后访问时间
            val lastAccess = moduleLastAccessTime[module.path] ?: 0L
            val idleTime = now - lastAccess
            
            // 超过闲置超时时间
            idleTime > MODULE_IDLE_TIMEOUT
        }
        
        if (modulesToRelease.isNotEmpty()) {
            logger.info("Releasing ${modulesToRelease.size} unused modules: ${modulesToRelease.map { it.path }}")
            
            modulesToRelease.forEach { module ->
                releaseModule(module.path)
            }
        }
    }
    
    /**
     * 释放指定模块
     */
    private fun releaseModule(modulePath: String) {
        // 跳过根项目
        if (modulePath == ":") return
        
        // 跳过 includeBuild 的模块
        val includeBuildPaths = GradleModuleManager.getIncludeBuildPaths(project)
        if (includeBuildPaths.any { modulePath.contains(it) }) {
            logger.info("Skipping includeBuild module: $modulePath")
            return
        }
        
        val success = GradleModuleManager.excludeModule(project, modulePath)
        if (success) {
            logger.info("Released module: $modulePath")
            moduleLastAccessTime.remove(modulePath)
        }
    }
    
    /**
     * 手动恢复所有被释放的模块
     */
    fun restoreAllModules() {
        val excludedModules = GradleModuleManager.getExcludedModules(project)
        if (excludedModules.isNotEmpty()) {
            val count = GradleModuleManager.includeModules(project, excludedModules.map { it.path })
            logger.info("Restored $count modules")
        }
    }
    
    private fun isGradleProject(): Boolean {
        val baseDir = project.guessProjectDir() ?: return false
        return baseDir.findChild("build.gradle") != null || 
               baseDir.findChild("build.gradle.kts") != null ||
               baseDir.findChild("settings.gradle") != null ||
               baseDir.findChild("settings.gradle.kts") != null
    }
    
    private fun isGradleProjectLoaded(): Boolean {
        val gradleSettings = GradleSettings.getInstance(project)
        return gradleSettings.linkedProjectsSettings.isNotEmpty()
    }
    
    private fun showGradleLoadIndicator() {
        val statusBar = WindowManager.getInstance().getStatusBar(project)
        val widget = statusBar?.getWidget("GradleBuddyWidget")
        if (widget is GradleBuddyWidget) {
            widget.showIndicator()
        }
    }
    
    fun dispose() {
        connection?.disconnect()
        scheduledExecutor.shutdown()
        moduleLastAccessTime.clear()
        openFileModules.clear()
    }
}
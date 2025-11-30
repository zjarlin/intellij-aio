package site.addzero.gradle.buddy

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent

class GradleBuddyPlugin : ProjectActivity {
    override suspend fun execute(project: Project) {
        // 初始化插件
        project.service<GradleBuddyService>().init()
        
        // 注册状态栏指示器
        registerStatusBarWidget(project)
    }
    
    private fun registerStatusBarWidget(project: Project) {
        val statusBar = WindowManager.getInstance().getStatusBar(project)
        if (statusBar != null) {
            val widget = GradleBuddyWidget(project)
            statusBar.addWidget(widget)
        }
    }
}

@Service
class GradleBuddyService(private val project: Project) {
    private var connection: MessageBusConnection? = null
    
    fun init() {
        // 监听文件编辑器事件
        connection = project.messageBus.connect()
        connection?.subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    checkGradleProject(file)
                }
            }
        )
    }
    
    private fun checkGradleProject(file: VirtualFile) {
        // 检查项目是否是Gradle项目且已加载
        if (isGradleProject() && !isGradleProjectLoaded()) {
            // 显示提示让用户加载Gradle项目
            showGradleLoadIndicator()
        }
    }
    
    private fun isGradleProject(): Boolean {
        // 检查项目根目录是否有build.gradle或build.gradle.kts文件
        val baseDir = project.basePath ?: return false
        val baseVirtualDir = project.baseDir
        return baseVirtualDir.findChild("build.gradle") != null || 
               baseVirtualDir.findChild("build.gradle.kts") != null ||
               baseVirtualDir.findChild("settings.gradle") != null ||
               baseVirtualDir.findChild("settings.gradle.kts") != null
    }
    
    private fun isGradleProjectLoaded(): Boolean {
        // 检查Gradle项目是否已加载
        val gradleSettings = GradleSettings.getInstance(project)
        return gradleSettings.linkedProjectsSettings.isNotEmpty()
    }
    
    private fun showGradleLoadIndicator() {
        // 获取状态栏并更新指示器
        val statusBar = WindowManager.getInstance().getStatusBar(project)
        val widget = statusBar?.getWidget("GradleBuddyWidget")
        if (widget is GradleBuddyWidget) {
            widget.showIndicator()
        }
    }
    
    fun dispose() {
        connection?.disconnect()
    }
}
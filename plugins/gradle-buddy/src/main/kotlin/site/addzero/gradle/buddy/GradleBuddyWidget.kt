package site.addzero.gradle.buddy

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.util.Consumer
import com.intellij.openapi.application.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gradle.service.project.open.linkAndSyncGradleProject
import java.awt.event.MouseEvent

/**
 * 状态栏 Widget - 显示 Gradle 项目加载状态
 */
class GradleBuddyWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.TextPresentation {
    
    private var statusBar: StatusBar? = null
    private var isIndicatorVisible = false
    
    override fun ID(): String = GradleBuddyWidgetFactory.WIDGET_ID
    
    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
    }
    
    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this
    
    override fun getText(): String {
        return if (isIndicatorVisible) "Gradle: Load Required" else ""
    }
    
    override fun getTooltipText(): String {
        return if (isIndicatorVisible) {
            "Click to load Gradle project"
        } else {
            "Gradle project loaded"
        }
    }
    
    override fun getAlignment(): Float = 0f
    
    override fun getClickConsumer(): Consumer<MouseEvent>? {
        return Consumer { 
            if (isIndicatorVisible) {
                loadGradleProject()
            }
        }
    }
    
    fun showIndicator() {
        isIndicatorVisible = true
        updateWidget()
    }
    
    fun hideIndicator() {
        isIndicatorVisible = false
        updateWidget()
    }
    
    private fun updateWidget() {
        statusBar?.updateWidget(ID())
    }
    
    private fun loadGradleProject() {
        // 获取项目根目录的 build.gradle.kts 或 build.gradle 路径
        val basePath = project.basePath ?: return
        val buildFile = listOf(
            "$basePath/build.gradle.kts",
            "$basePath/build.gradle",
            "$basePath/settings.gradle.kts",
            "$basePath/settings.gradle"
        ).firstOrNull { java.io.File(it).exists() } ?: return
        
        // 触发 Gradle 项目链接和同步
        @Suppress("OPT_IN_USAGE")
        GlobalScope.launch(Dispatchers.EDT) {
            linkAndSyncGradleProject(project, buildFile)
            hideIndicator()
        }
    }
    
    override fun dispose() {
        statusBar = null
    }
}
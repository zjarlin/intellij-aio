package site.addzero.gradle.buddy

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import org.jetbrains.plugins.gradle.settings.GradleSettings

class GradleBuddyWidget(private val project: Project) : StatusBarWidget {
    private var statusBar: StatusBar? = null
    private var isIndicatorVisible = false
    
    override fun ID(): String = "GradleBuddyWidget"
    
    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
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
        // 触发Gradle项目导入
        val gradleSettings = GradleSettings.getInstance(project)
        // 这里应该触发Gradle项目同步，但我们需要更复杂的实现
        // 暂时只是隐藏指示器
        hideIndicator()
        
        // 实际的Gradle导入需要使用Gradle工具窗口或相关服务
        // 这里我们只是演示基本功能
    }
    
    override fun dispose() {
        statusBar = null
    }
}
package site.addzero.autoddl.jimmer.toolwindow

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import javax.swing.JComponent

/**
 * Jimmer DDL 工具窗口
 */
class JimmerDdlToolWindowFactory : ToolWindowFactory {
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // 创建日志面板
        val logPanel = DdlLogPanel(project)
        
        // 注册为服务，方便其他地方访问
        project.putUserData(DDL_LOG_PANEL_KEY, logPanel)
        
        val content = ContentFactory.getInstance().createContent(
            logPanel,
            "执行日志",
            false
        )
        toolWindow.contentManager.addContent(content)
    }
    
    companion object {
        val DDL_LOG_PANEL_KEY = com.intellij.openapi.util.Key.create<DdlLogPanel>("DDL_LOG_PANEL")
        
        /**
         * 获取日志面板
         */
        fun getLogPanel(project: Project): DdlLogPanel? {
            return project.getUserData(DDL_LOG_PANEL_KEY)
        }
    }
}

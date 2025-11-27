package site.addzero.diagnostic.problemsview

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory

class AiFixProblemsInjector : ProjectActivity {
    
    override suspend fun execute(project: Project) {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        
        // 延迟执行，确保 Problems 工具窗口已创建
        toolWindowManager.invokeLater {
            val problemsToolWindow = toolWindowManager.getToolWindow("Problems View")
                ?: toolWindowManager.getToolWindow("Problems")
            
            if (problemsToolWindow != null) {
                val contentManager = problemsToolWindow.contentManager
                
                // 检查是否已添加
                if (contentManager.findContent("Problem4Ai") == null) {
                    val panel = AiFixPanel(project)
                    val content = ContentFactory.getInstance().createContent(panel, "Problem4Ai", false)
                    contentManager.addContent(content)
                }
            }
        }
    }
}

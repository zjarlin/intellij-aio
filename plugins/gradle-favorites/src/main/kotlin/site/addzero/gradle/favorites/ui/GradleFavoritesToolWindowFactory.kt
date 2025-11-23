package site.addzero.gradle.favorites.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class GradleFavoritesToolWindowFactory : ToolWindowFactory {
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val favoritesPanel = GradleFavoritesPanel(project)
        val content = ContentFactory.getInstance().createContent(favoritesPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

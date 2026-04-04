package site.addzero.composebuddy.designer.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.designer.ui.ComposeDesignerPanel

class ComposeDesignerToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.setStripeTitle(ComposeBuddyBundle.message("designer.toolwindow.title"))
        val content = ContentFactory.getInstance().createContent(
            ComposeDesignerPanel(project),
            ComposeBuddyBundle.message("designer.toolwindow.title"),
            false,
        )
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}

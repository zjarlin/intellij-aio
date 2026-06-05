package site.addzero.composebuddy.previewsandbox

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class ComposePreviewSandboxToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.setStripeTitle(TOOL_WINDOW_ID)
        val panel = ComposePreviewSandboxPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "Preview", false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
        ComposePreviewSandboxService.getInstance(project).showCurrentSessionLater()
    }

    override fun shouldBeAvailable(project: Project): Boolean = true

    companion object {
        const val TOOL_WINDOW_ID: String = "KMP Buddy Preview"
    }
}


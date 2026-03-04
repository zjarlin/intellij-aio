package site.addzero.vibetask.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

class OpenVibeTaskPanelAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Vibe Task")
        toolWindow?.show()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}

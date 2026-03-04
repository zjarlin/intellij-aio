package site.addzero.vibetask.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import site.addzero.vibetask.model.VibeTask
import site.addzero.vibetask.service.VibeTaskService

class AddTaskAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = VibeTaskService.getInstance(project)

        // 快速输入对话框
        val content = Messages.showInputDialog(
            project,
            "记录你的 vibe coding 灵感:",
            "添加 Vibe Task",
            Messages.getQuestionIcon()
        ) ?: return

        if (content.isNotBlank()) {
            service.addTask(content.trim())

            // 打开面板
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Vibe Task")
            toolWindow?.show()
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

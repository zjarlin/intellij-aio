package site.addzero.vibetask.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.ToolWindowManager
import site.addzero.vibetask.service.VibeTaskService
import site.addzero.vibetask.ui.FastVibePopupPanel
import java.awt.Dimension

/**
 * Fast Vibe 下拉按钮 Action
 * 仿照 IDEA 任务下拉设计，显示当前模块，点击展开面板
 */
class FastVibeDropdownAction : DumbAwareAction(
    "Fast Vibe",
    "快速记录当前模块的想法",
    com.intellij.icons.AllIcons.Actions.Lightning
) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // 创建弹出面板
        val popupPanel = FastVibePopupPanel(project)

        // 获取触发组件
        val inputEvent = e.inputEvent
        val component = inputEvent?.component

        val jComponent = component as? javax.swing.JComponent
        if (jComponent != null) {
            popupPanel.showPopup(jComponent)
        } else {
            // 备用：直接打开工具窗口
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Vibe Task")
            toolWindow?.show()
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null

        // 尝试更新显示的文本为当前模块名
        project?.let { p ->
            try {
                val service = VibeTaskService.getInstance(p)
                val fileEditorManager = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(p)
                val currentFile = fileEditorManager.selectedFiles.firstOrNull()

                val module = currentFile?.let { file ->
                    service.getModuleForFile(file.path)
                }

                e.presentation.text = if (module != null) {
                    "⚡ ${module.name}"
                } else {
                    "⚡ Fast Vibe"
                }
            } catch (_: Exception) {
                e.presentation.text = "⚡ Fast Vibe"
            }
        }
    }
}
package site.addzero.smart.intentions.hiddenfiles.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import site.addzero.smart.intentions.hiddenfiles.HiddenFilesProjectService
import site.addzero.smart.intentions.hiddenfiles.IdeKitBundle

class ToggleShowHiddenFilesAction : ToggleAction(
    IdeKitBundle.message("action.toggle.show.text"),
    IdeKitBundle.message("action.toggle.show.description"),
    AllIcons.Actions.Show,
), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }

    override fun isSelected(event: AnActionEvent): Boolean {
        val project = event.project ?: return false
        return project.service<HiddenFilesProjectService>().isShowHiddenFiles()
    }

    override fun setSelected(event: AnActionEvent, state: Boolean) {
        val project = event.project ?: return
        project.service<HiddenFilesProjectService>().setShowHiddenFiles(state)
    }
}

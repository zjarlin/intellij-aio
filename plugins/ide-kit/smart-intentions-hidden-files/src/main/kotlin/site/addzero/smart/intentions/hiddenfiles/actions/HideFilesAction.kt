package site.addzero.smart.intentions.hiddenfiles.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import site.addzero.smart.intentions.hiddenfiles.HiddenFilesProjectService
import site.addzero.smart.intentions.hiddenfiles.IdeKitBundle

class HideFilesAction : DumbAwareAction(
    IdeKitBundle.message("action.hide.text"),
    IdeKitBundle.message("action.hide.description"),
    AllIcons.Actions.ToggleVisibility,
) {
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(event: AnActionEvent) {
        val project = event.project
        val files = HiddenFilesActionSupport.getSelectedFiles(event)
        val service = project?.service<HiddenFilesProjectService>()

        event.presentation.isVisible = project != null && files.isNotEmpty()
        event.presentation.isEnabled = service != null && files.any { !service.isMarkedHidden(it) }
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val files = HiddenFilesActionSupport.getSelectedFiles(event)
        if (files.isEmpty()) {
            return
        }

        project.service<HiddenFilesProjectService>().hide(files)
    }
}

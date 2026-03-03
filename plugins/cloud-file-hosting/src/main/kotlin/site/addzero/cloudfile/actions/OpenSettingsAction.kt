package site.addzero.cloudfile.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import site.addzero.cloudfile.settings.CloudFileConfigurable

/**
 * Action to open Cloud File Hosting settings
 */
class OpenSettingsAction : AnAction(
    "Cloud Hosting Settings",
    "Open Cloud File Hosting settings",
    com.intellij.icons.AllIcons.Nodes.PpLibFolder
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ShowSettingsUtil.getInstance().showSettingsDialog(project, CloudFileConfigurable::class.java)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}

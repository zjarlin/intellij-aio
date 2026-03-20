package site.addzero.gitee.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.ui.Messages
import site.addzero.gitee.settings.GiteeConfigurable
import site.addzero.gitee.settings.GiteeSettings
import site.addzero.gitee.ui.clone.GiteeCloneDialog

/**
 * Action to clone a repository from Gitee
 */
class CloneFromGiteeAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        // Always visible
        e.presentation.isEnabledAndVisible = true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val settings = GiteeSettings.getInstance()

        if (!settings.hasCloneAccountConfigured()) {
            val openSettings = Messages.showYesNoDialog(
                project,
                "Please configure Gitee authentication first. Choose either 'Username / Password' or 'Access Token' in settings, then come back to clone.",
                "Gitee Authentication Required",
                "Open Settings",
                "Cancel",
                Messages.getInformationIcon()
            ) == Messages.YES

            if (openSettings) {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, GiteeConfigurable::class.java)
            }
            return
        }

        val dialog = GiteeCloneDialog(project)
        dialog.showAndGet()
    }
}

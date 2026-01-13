package site.addzero.autoupdate

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import git4idea.repo.GitRepositoryManager

/**
 * Manual pull action for triggering native Update Project
 */
class ManualPullAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val action = ActionManager.getInstance().getAction("Vcs.UpdateProject") ?: return

        ActionUtil.performAction(action, e)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val enabled = project?.let {
            GitRepositoryManager.getInstance(it).repositories.isNotEmpty()
        } ?: false
        e.presentation.isEnabledAndVisible = enabled
    }
}

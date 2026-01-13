package site.addzero.autoupdate

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import git4idea.repo.GitRepositoryManager

/**
 * Manual pull action for triggering native Update Project
 */
class ManualPullAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val action = ActionManager.getInstance().getAction("Vcs.UpdateProject") ?: return

        // Create a proper event to ensure project and context are passed correctly
        val event = AnActionEvent.createFromAnAction(
            action,
            e.inputEvent,
            e.place,
            e.dataContext
        )
        ActionUtil.performActionDumbAwareWithCallbacks(action, event)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val enabled = project?.let {
            GitRepositoryManager.getInstance(it).repositories.isNotEmpty()
        } ?: false
        e.presentation.isEnabledAndVisible = enabled
    }
}

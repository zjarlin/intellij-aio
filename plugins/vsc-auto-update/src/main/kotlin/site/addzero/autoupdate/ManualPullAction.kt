package site.addzero.autoupdate

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitCommandResult
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepositoryManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType

/**
 * Manual pull action for triggering pull from remote
 */
class ManualPullAction : AnAction() {

    private val log = Logger.getInstance(ManualPullAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val settings = AutoUpdateSettings.getInstance()

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Pulling from Remote", true) {
            override fun run(indicator: ProgressIndicator) {
                val repositoryManager = GitRepositoryManager.getInstance(project)
                val repositories = repositoryManager.repositories

                if (repositories.isEmpty()) {
                    showNotification("No Git Repository", "Project is not a git repository", NotificationType.WARNING)
                    return
                }

                repositories.forEach { repository ->
                    indicator.text = "Pulling ${repository.currentBranchName}..."
                    val result = if (settings.pullRebase) {
                        executePullRebase(repository)
                    } else {
                        executePull(repository)
                    }

                    if (result.success()) {
                        showNotification(
                            "Pull Successful",
                            "Pulled latest changes for '${repository.currentBranchName}'",
                            NotificationType.INFORMATION
                        )
                    } else {
                        val error = result.errorOutput.joinToString("\n").take(200)
                        showNotification(
                            "Pull Failed",
                            "Failed to pull: $error",
                            NotificationType.ERROR
                        )
                    }
                }
            }
        })
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val enabled = project?.let {
            GitRepositoryManager.getInstance(it).repositories.isNotEmpty()
        } ?: false
        e.presentation.isEnabledAndVisible = enabled
    }

    private fun executePull(repository: git4idea.repo.GitRepository): GitCommandResult {
        val handler = GitLineHandler(repository.project, repository.root, GitCommand.PULL)
        handler.addParameters("--no-commit")
        return Git.getInstance().runCommand(handler)
    }

    private fun executePullRebase(repository: git4idea.repo.GitRepository): GitCommandResult {
        val handler = GitLineHandler(repository.project, repository.root, GitCommand.PULL)
        handler.addParameters("--rebase")
        return Git.getInstance().runCommand(handler)
    }

    private fun showNotification(title: String, content: String, type: NotificationType) {
        val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("VSCAutoUpdate")
        notificationGroup?.createNotification(title, content, type)?.notify(null)
    }
}

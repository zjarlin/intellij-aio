package site.addzero.gitee.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepositoryManager

/**
 * Action to sync forked repository with upstream
 */
class SyncForkAction : AnAction() {

    private val log = Logger.getInstance(SyncForkAction::class.java)

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val enabled = project?.let {
            GitRepositoryManager.getInstance(it).repositories.isNotEmpty()
        } ?: false
        e.presentation.isEnabledAndVisible = enabled
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val repository = GitRepositoryManager.getInstance(project).repositories.firstOrNull()
        if (repository == null) {
            showNotification(project, "No Git repository found", NotificationType.ERROR)
            return
        }

        val repoPath = repository.root
        val git = service<Git>()

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Syncing Fork with Upstream...",
            true
        ) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                try {
                    indicator.text = "Checking for upstream remote..."

                    // Check if upstream remote exists
                    val remotes = repository.remotes
                    val hasUpstream = remotes.any { it.name == "upstream" }

                    if (!hasUpstream) {
                        // Ask user for upstream URL
                        val upstreamUrl = com.intellij.openapi.ui.Messages.showInputDialog(
                            project,
                            "No upstream remote found. Enter upstream repository URL:",
                            "Add Upstream Remote",
                            com.intellij.openapi.ui.Messages.getQuestionIcon()
                        )

                        if (upstreamUrl.isNullOrBlank()) {
                            return
                        }

                        indicator.text = "Adding upstream remote..."
                        val addRemoteHandler = GitLineHandler(project, repoPath, GitCommand.REMOTE)
                        addRemoteHandler.addParameters("add", "upstream", upstreamUrl)
                        val addResult = git.runCommand(addRemoteHandler)

                        if (!addResult.success()) {
                            showNotification(project, "Failed to add upstream remote: ${addResult.errorOutputAsHtmlString}", NotificationType.ERROR)
                            return
                        }
                    }

                    // Fetch upstream
                    indicator.text = "Fetching upstream..."
                    val fetchHandler = GitLineHandler(project, repoPath, GitCommand.FETCH)
                    fetchHandler.addParameters("upstream")
                    val fetchResult = git.runCommand(fetchHandler)

                    if (!fetchResult.success()) {
                        showNotification(project, "Failed to fetch upstream: ${fetchResult.errorOutputAsHtmlString}", NotificationType.ERROR)
                        return
                    }

                    // Get current branch
                    val currentBranch = repository.currentBranch?.name ?: "master"

                    // Merge upstream into current branch
                    indicator.text = "Merging upstream/$currentBranch..."
                    val mergeHandler = GitLineHandler(project, repoPath, GitCommand.MERGE)
                    mergeHandler.addParameters("upstream/$currentBranch")
                    val mergeResult = git.runCommand(mergeHandler)

                    if (mergeResult.success()) {
                        showNotification(project, "Successfully synced fork with upstream", NotificationType.INFORMATION)
                    } else {
                        showNotification(
                            project,
                            "Merge completed with conflicts. Please resolve conflicts manually.",
                            NotificationType.WARNING
                        )
                    }

                } catch (e: Exception) {
                    log.error("Error syncing fork", e)
                    showNotification(project, "Error syncing fork: ${e.message}", NotificationType.ERROR)
                }
            }
        })
    }

    private fun showNotification(project: com.intellij.openapi.project.Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("GiteeNotifications")
            .createNotification(message, type)
            .notify(project)
    }
}

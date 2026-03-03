package site.addzero.gitee.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import git4idea.repo.GitRepositoryManager
import site.addzero.gitee.api.GiteeApiClient
import site.addzero.gitee.api.GiteeApiException
import site.addzero.gitee.settings.GiteeSettings

/**
 * Action to create a pull request on Gitee
 */
class CreatePullRequestAction : AnAction() {

    private val log = Logger.getInstance(CreatePullRequestAction::class.java)

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
        val settings = GiteeSettings.getInstance()

        if (!settings.isConfigured()) {
            showNotification(project, "Please configure Gitee access token in Settings → Tools → Gitee", NotificationType.ERROR)
            return
        }

        val repository = GitRepositoryManager.getInstance(project).repositories.firstOrNull()
        if (repository == null) {
            showNotification(project, "No Git repository found", NotificationType.ERROR)
            return
        }

        // Get repository info from remote URL
        val remoteUrl = repository.remotes.firstOrNull()?.firstUrl
        if (remoteUrl == null || !remoteUrl.contains("gitee.com")) {
            showNotification(project, "No Gitee remote found. Please share project on Gitee first.", NotificationType.WARNING)
            return
        }

        // Parse owner and repo from remote URL
        val repoInfo = parseRepoInfo(remoteUrl)
        if (repoInfo == null) {
            showNotification(project, "Could not parse repository information from remote URL", NotificationType.ERROR)
            return
        }

        val currentBranch = repository.currentBranch?.name ?: "master"

        // Show simple input dialog for PR details
        val title = com.intellij.openapi.ui.Messages.showInputDialog(
            project,
            "Enter pull request title (from '$currentBranch' to 'master'):",
            "Create Pull Request on Gitee",
            com.intellij.openapi.ui.Messages.getQuestionIcon()
        ) ?: return

        if (title.isBlank()) {
            showNotification(project, "Pull request title is required", NotificationType.WARNING)
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Creating Pull Request on Gitee...",
            true
        ) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                try {
                    val apiClient = GiteeApiClient(settings.accessToken)

                    val pr = apiClient.createPullRequest(
                        owner = repoInfo.first,
                        repo = repoInfo.second,
                        title = title,
                        body = "Created from IntelliJ IDEA",
                        head = currentBranch,
                        base = "master"
                    )

                    showNotification(
                        project,
                        "Pull request created: ${pr.htmlUrl}",
                        NotificationType.INFORMATION
                    )

                } catch (e: GiteeApiException) {
                    log.error("Failed to create pull request", e)
                    showNotification(project, "Failed to create PR: ${e.message}", NotificationType.ERROR)
                } catch (e: Exception) {
                    log.error("Unexpected error creating PR", e)
                    showNotification(project, "Unexpected error: ${e.message}", NotificationType.ERROR)
                }
            }
        })
    }

    private fun parseRepoInfo(remoteUrl: String): Pair<String, String>? {
        // Handle HTTPS: https://gitee.com/owner/repo.git
        // Handle SSH: git@gitee.com:owner/repo.git
        return when {
            remoteUrl.startsWith("https://gitee.com/") -> {
                val path = remoteUrl.removePrefix("https://gitee.com/").removeSuffix(".git")
                val parts = path.split("/")
                if (parts.size >= 2) parts[0] to parts[1] else null
            }
            remoteUrl.startsWith("git@gitee.com:") -> {
                val path = remoteUrl.removePrefix("git@gitee.com:").removeSuffix(".git")
                val parts = path.split("/")
                if (parts.size >= 2) parts[0] to parts[1] else null
            }
            else -> null
        }
    }

    private fun showNotification(project: com.intellij.openapi.project.Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("GiteeNotifications")
            .createNotification(message, type)
            .notify(project)
    }
}

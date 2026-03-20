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
 * Action to view pull requests on Gitee
 */
class ViewPullRequestsAction : AnAction() {

    private val log = Logger.getInstance(ViewPullRequestsAction::class.java)

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

        if (!settings.hasAccessToken()) {
            showNotification(project, "This action requires Access Token mode. Please configure a Gitee access token in Settings → Version Control → Gitee.", NotificationType.ERROR)
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
            showNotification(project, "No Gitee remote found", NotificationType.WARNING)
            return
        }

        // Parse owner and repo from remote URL
        val repoInfo = parseRepoInfo(remoteUrl)
        if (repoInfo == null) {
            showNotification(project, "Could not parse repository information", NotificationType.ERROR)
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Loading Pull Requests from Gitee...",
            true
        ) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                try {
                    val apiClient = GiteeApiClient(settings.accessToken)
                    val prs = apiClient.getPullRequests(repoInfo.first, repoInfo.second, "open")

                    if (prs.isEmpty()) {
                        showNotification(project, "No open pull requests found", NotificationType.INFORMATION)
                    } else {
                        val prList = prs.take(5).joinToString("\n") { "#${it.number}: ${it.title}" }
                        val message = "Open Pull Requests (${prs.size} total):\n$prList"
                        showNotification(project, message, NotificationType.INFORMATION)
                    }

                    // Open Gitee PR page in browser
                    val url = "https://gitee.com/${repoInfo.first}/${repoInfo.second}/pulls"
                    com.intellij.ide.BrowserUtil.open(url)

                } catch (e: GiteeApiException) {
                    log.error("Failed to load pull requests", e)
                    showNotification(project, "Failed to load PRs: ${e.message}", NotificationType.ERROR)
                } catch (e: Exception) {
                    log.error("Unexpected error", e)
                    showNotification(project, "Unexpected error: ${e.message}", NotificationType.ERROR)
                }
            }
        })
    }

    private fun parseRepoInfo(remoteUrl: String): Pair<String, String>? {
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

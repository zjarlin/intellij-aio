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
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import site.addzero.gitee.api.GiteeApiClient
import site.addzero.gitee.api.GiteeApiException
import site.addzero.gitee.settings.GiteeSettings
import site.addzero.gitee.ui.ShareProjectDialog

/**
 * Action to share current project on Gitee
 */
class ShareProjectOnGiteeAction : AnAction() {

    private val log = Logger.getInstance(ShareProjectOnGiteeAction::class.java)

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

        val dialog = ShareProjectDialog(project)
        if (!dialog.showAndGet()) return

        val repoName = dialog.getRepoName()
        val description = dialog.getDescription()
        val isPrivate = dialog.isPrivate()

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Sharing Project on Gitee...",
            true
        ) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                try {
                    indicator.text = "Creating repository on Gitee..."

                    val apiClient = GiteeApiClient(settings.accessToken)

                    // Check if Gitee remote already exists
                    val repository = GitRepositoryManager.getInstance(project).repositories.firstOrNull()
                    if (repository != null && hasGiteeRemote(repository)) {
                        showNotification(project, "Project already has a Gitee remote", NotificationType.WARNING)
                        return
                    }

                    // Create repository on Gitee
                    val repo = apiClient.createRepo(repoName, description, isPrivate, false)

                    indicator.text = "Pushing to Gitee..."

                    // Add remote and push
                    val git = service<Git>()
                    val repoPath = repository?.root ?: findProjectBaseDir(project) ?: return

                    // Add remote
                    val addRemoteHandler = GitLineHandler(project, repoPath, GitCommand.REMOTE)
                    addRemoteHandler.addParameters("add", "origin", repo.cloneUrl)
                    git.runCommand(addRemoteHandler)

                    // Get current branch
                    val currentBranch = repository?.currentBranch?.name ?: "master"

                    // Push to Gitee
                    val pushHandler = GitLineHandler(project, repoPath, GitCommand.PUSH)
                    pushHandler.addParameters("-u", "origin", currentBranch)
                    val pushResult = git.runCommand(pushHandler)

                    if (pushResult.success()) {
                        showNotification(
                            project,
                            "Project shared on Gitee: ${repo.htmlUrl}",
                            NotificationType.INFORMATION
                        )
                    } else {
                        showNotification(
                            project,
                            "Repository created but push failed: ${pushResult.errorOutputAsJoinedString}",
                            NotificationType.WARNING
                        )
                    }

                } catch (e: GiteeApiException) {
                    log.error("Failed to share project on Gitee", e)
                    showNotification(project, "Failed to share project: ${e.message}", NotificationType.ERROR)
                } catch (e: Exception) {
                    log.error("Unexpected error sharing project", e)
                    showNotification(project, "Unexpected error: ${e.message}", NotificationType.ERROR)
                }
            }
        })
    }

    private fun findProjectBaseDir(project: Project): VirtualFile? {
        return project.basePath?.let {
            com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(it)
        }
    }

    private fun hasGiteeRemote(repository: GitRepository): Boolean {
        return repository.remotes.any { remote ->
            remote.firstUrl?.contains("gitee.com") == true
        }
    }

    private fun showNotification(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("GiteeNotifications")
            .createNotification(message, type)
            .notify(project)
    }
}

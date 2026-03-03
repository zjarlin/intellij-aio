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

                    // Get user info first
                    val user = apiClient.getUser()

                    // Check if repository with same name exists
                    val repo = try {
                        val existingRepo = apiClient.getRepo(user.login, repoName)
                        // Repository exists, use it
                        log.info("Repository already exists: ${existingRepo.name}")
                        existingRepo
                    } catch (e: GiteeApiException) {
                        // Repository doesn't exist, create it
                        log.info("Creating new repository: $repoName")
                        apiClient.createRepo(repoName, description, isPrivate, false)
                    }

                    indicator.text = "Pushing to Gitee: ${repo.name}..."

                    // Add remote and push
                    val git = service<Git>()
                    val repoPath = repository?.root ?: findProjectBaseDir(project) ?: return

                    // Get the clone URL - use htmlUrl if cloneUrl is null
                    val remoteUrl = repo.cloneUrl?.takeIf { it.isNotBlank() }
                        ?: repo.htmlUrl?.takeIf { it.isNotBlank() }?.let { "$it.git" }
                        ?: "https://gitee.com/${repo.owner.login}/$repoName.git"

                    // Check if origin remote exists
                    val hasOrigin = repository?.remotes?.any { it.name == "origin" } ?: false

                    // Add or set remote
                    val remoteHandler = GitLineHandler(project, repoPath, GitCommand.REMOTE)
                    if (hasOrigin) {
                        remoteHandler.addParameters("set-url", "origin", remoteUrl)
                    } else {
                        remoteHandler.addParameters("add", "origin", remoteUrl)
                    }
                    val remoteResult = git.runCommand(remoteHandler)
                    if (!remoteResult.success()) {
                        showNotification(project, "Failed to add remote: ${remoteResult.errorOutputAsJoinedString}", NotificationType.ERROR)
                        return
                    }

                    // Get current branch
                    val currentBranch = repository?.currentBranch?.name ?: "master"

                    // Check if there are commits
                    val logHandler = GitLineHandler(project, repoPath, GitCommand.LOG)
                    logHandler.addParameters("-1", "--oneline")
                    val logResult = git.runCommand(logHandler)
                    val hasCommits = logResult.success() && logResult.output.isNotEmpty()

                    if (!hasCommits) {
                        // Need to make initial commit
                        indicator.text = "Creating initial commit..."

                        // Add all files
                        val addHandler = GitLineHandler(project, repoPath, GitCommand.ADD)
                        addHandler.addParameters(".")
                        git.runCommand(addHandler)

                        // Commit
                        val commitHandler = GitLineHandler(project, repoPath, GitCommand.COMMIT)
                        commitHandler.addParameters("-m", "Initial commit")
                        val commitResult = git.runCommand(commitHandler)
                        if (!commitResult.success()) {
                            showNotification(project, "Failed to create initial commit: ${commitResult.errorOutputAsJoinedString}", NotificationType.WARNING)
                            return
                        }
                    }

                    // Push to Gitee
                    indicator.text = "Pushing to Gitee..."
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
                    val message = when {
                        e.message?.contains("已存在") == true -> "Repository already exists on Gitee"
                        e.message?.contains("access_token") == true -> "Invalid access token"
                        else -> "Failed to share project: ${e.message}"
                    }
                    showNotification(project, message, NotificationType.ERROR)
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

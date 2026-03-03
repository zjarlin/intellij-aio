package site.addzero.gitee.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.ProjectManager
import java.io.File

/**
 * Action to clone a repository from Gitee
 */
class CloneFromGiteeAction : AnAction() {

    private val log = Logger.getInstance(CloneFromGiteeAction::class.java)

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        // Always visible
        e.presentation.isEnabledAndVisible = true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project

        // Show dialog for repository URL
        val repoUrl = com.intellij.openapi.ui.Messages.showInputDialog(
            project,
            "Enter Gitee repository URL or owner/repo (e.g., username/repository):",
            "Clone from Gitee",
            com.intellij.openapi.ui.Messages.getQuestionIcon()
        ) ?: return

        if (repoUrl.isBlank()) {
            return
        }

        // Convert owner/repo format to full URL
        val fullUrl = when {
            repoUrl.startsWith("http") -> repoUrl
            repoUrl.startsWith("git@") -> repoUrl
            repoUrl.contains("/") -> "https://gitee.com/$repoUrl.git"
            else -> {
                showNotification(project, "Invalid repository format", NotificationType.ERROR)
                return
            }
        }

        // Get destination directory
        val fileChooser = com.intellij.openapi.fileChooser.FileChooserFactory.getInstance()
            .createFileChooser(
                com.intellij.openapi.fileChooser.FileChooserDescriptor(false, true, false, false, false, false)
                    .withTitle("Select Destination Directory"),
                project,
                null
            )

        val selectedFiles = fileChooser.choose(project)
        if (selectedFiles.isEmpty()) return

        val destinationDir = selectedFiles[0]
        val repoName = fullUrl.substringAfterLast("/").removeSuffix(".git")
        val targetDir = File(destinationDir.path, repoName)

        if (targetDir.exists()) {
            showNotification(project, "Directory '$repoName' already exists", NotificationType.ERROR)
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Cloning from Gitee...",
            true
        ) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                try {
                    indicator.text = "Cloning $repoName..."

                    // Create target directory
                    targetDir.mkdirs()

                    // Clone the repository using native git command
                    val process = ProcessBuilder("git", "clone", fullUrl, targetDir.absolutePath)
                        .inheritIO()
                        .start()

                    val exitCode = process.waitFor()

                    if (exitCode == 0) {
                        com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByPath(targetDir.absolutePath)

                        showNotification(
                            project,
                            "Successfully cloned to ${targetDir.absolutePath}",
                            NotificationType.INFORMATION
                        )

                        // Ask if user wants to open the project
                        val openProject = com.intellij.openapi.ui.Messages.showYesNoDialog(
                            project,
                            "Clone completed. Open project '$repoName'?",
                            "Clone Complete",
                            com.intellij.openapi.ui.Messages.getQuestionIcon()
                        ) == com.intellij.openapi.ui.Messages.YES

                        if (openProject) {
                            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                                ProjectManager.getInstance().loadAndOpenProject(targetDir.absolutePath)
                            }
                        }
                    } else {
                        showNotification(project, "Failed to clone repository (exit code: $exitCode)", NotificationType.ERROR)
                    }

                } catch (e: Exception) {
                    log.error("Failed to clone repository", e)
                    showNotification(project, "Failed to clone: ${e.message}", NotificationType.ERROR)
                }
            }
        })
    }

    private fun showNotification(project: com.intellij.openapi.project.Project?, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("GiteeNotifications")
            .createNotification(message, type)
            .notify(project)
    }
}

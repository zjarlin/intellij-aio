package site.addzero.composebuddy.deadcode

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import java.nio.file.Paths

class DeadCodeRestoreAction : AnAction(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val selectedFile = event.getData(CommonDataKeys.VIRTUAL_FILE)
        event.presentation.isEnabledAndVisible = event.project != null &&
            selectedFile != null &&
            selectedFile.name == DeadCodeConstants.MANIFEST_FILE_NAME
        event.presentation.text = "KMP Buddy: Restore Dead Code"
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val manifestVirtualFile = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val manifestPath = Paths.get(manifestVirtualFile.path)
        val result = WriteCommandAction.writeCommandAction(project)
            .withName("Restore KMP Buddy dead code")
            .compute<DeadCodeRestoreResult, RuntimeException> {
                DeadCodeRestorer(project).restore(manifestPath)
            }
        val message = buildString {
            append("Restored ${result.restoredFileCount} file(s).")
            if (result.conflictCount > 0) {
                append(" Conflicts: ${result.conflictCount}.")
            }
            if (result.missingMirrorFileCount > 0) {
                append(" Missing mirror files: ${result.missingMirrorFileCount}.")
            }
        }
        notify(
            project = project,
            message = message,
            type = if (result.conflictCount == 0 && result.missingMirrorFileCount == 0) {
                NotificationType.INFORMATION
            } else {
                NotificationType.WARNING
            },
        )
    }

    private fun notify(
        project: Project,
        message: String,
        type: NotificationType,
    ) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("KMP Buddy Notifications")
            .createNotification(message, type)
            .notify(project)
    }
}

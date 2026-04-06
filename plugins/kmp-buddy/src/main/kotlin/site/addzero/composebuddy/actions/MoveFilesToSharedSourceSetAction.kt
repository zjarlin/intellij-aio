package site.addzero.composebuddy.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.support.MoveToSharedSourceSetSupport

class MoveFilesToSharedSourceSetAction : AnAction(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val selectedFiles = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        val enabled = event.project != null &&
            !selectedFiles.isNullOrEmpty() &&
            selectedFiles.any { file ->
                file.isDirectory || file.extension?.equals("kt", ignoreCase = true) == true
            }
        event.presentation.isEnabledAndVisible = enabled
        event.presentation.text = ComposeBuddyBundle.message("action.move.files.to.shared")
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val selectedFiles = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY).orEmpty().toList()
        val kotlinFiles = MoveToSharedSourceSetSupport.collectKotlinFiles(selectedFiles)
        if (kotlinFiles.isEmpty()) {
            notify(
                project = project,
                message = ComposeBuddyBundle.message("action.move.files.to.shared.none"),
                type = NotificationType.WARNING,
            )
            return
        }
        val plannedFiles = kotlinFiles.mapNotNull(MoveToSharedSourceSetSupport::buildPlan)
        val validPlans = MoveToSharedSourceSetSupport.filterConflictingPlans(plannedFiles)
        if (validPlans.isEmpty()) {
            notify(
                project = project,
                message = ComposeBuddyBundle.message("action.move.files.to.shared.none"),
                type = NotificationType.WARNING,
            )
            return
        }
        val movedFileCount = MoveToSharedSourceSetSupport.movePlans(project, validPlans)
        val skippedFileCount = kotlinFiles.size - movedFileCount
        val message = if (skippedFileCount > 0) {
            ComposeBuddyBundle.message(
                "action.move.files.to.shared.summary.with.skipped",
                movedFileCount,
                skippedFileCount,
            )
        } else {
            ComposeBuddyBundle.message("action.move.files.to.shared.summary", movedFileCount)
        }
        notify(
            project = project,
            message = message,
            type = if (movedFileCount > 0) NotificationType.INFORMATION else NotificationType.WARNING,
        )
    }

    private fun notify(
        project: com.intellij.openapi.project.Project,
        message: String,
        type: NotificationType,
    ) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("KMP Buddy Notifications")
            .createNotification(message, type)
            .notify(project)
    }
}

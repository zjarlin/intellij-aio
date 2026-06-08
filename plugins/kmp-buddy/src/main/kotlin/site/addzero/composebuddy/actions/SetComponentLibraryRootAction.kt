package site.addzero.composebuddy.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.support.ComponentLibrarySupport

class SetComponentLibraryRootAction : AnAction(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val directory = resolveSelectedDirectory(event)
        event.presentation.isEnabledAndVisible = event.project != null && directory != null
        event.presentation.text = ComposeBuddyBundle.message("action.set.component.library.root")
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val directory = resolveSelectedDirectory(event) ?: return
        if (!ComponentLibrarySupport.rememberComponentLibraryRoot(directory)) {
            return
        }
        NotificationGroupManager.getInstance()
            .getNotificationGroup("KMP Buddy Notifications")
            .createNotification(
                ComposeBuddyBundle.message("action.set.component.library.root.updated", directory.path),
                NotificationType.INFORMATION,
            )
            .notify(project)
    }

    private fun resolveSelectedDirectory(event: AnActionEvent) = event
        .getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        .orEmpty()
        .singleOrNull()
        ?.takeIf { file ->
            file.isValid && file.isDirectory && file.isInLocalFileSystem
        }
}

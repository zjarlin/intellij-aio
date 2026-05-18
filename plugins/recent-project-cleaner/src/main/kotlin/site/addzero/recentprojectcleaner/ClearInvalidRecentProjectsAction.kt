package site.addzero.recentprojectcleaner

import com.intellij.icons.AllIcons
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class ClearInvalidRecentProjectsAction : DumbAwareAction(
    "Clear Invalid Projects",
    "Remove missing recent projects from the Welcome screen",
    AllIcons.Actions.GC,
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val invalidCount = findInvalidPaths().size
        event.presentation.isEnabledAndVisible = true
        event.presentation.isEnabled = invalidCount > 0
        event.presentation.text = if (invalidCount > 0) {
            "Clear Invalid Projects ($invalidCount)"
        } else {
            "Clear Invalid Projects"
        }
    }

    override fun actionPerformed(event: AnActionEvent) {
        val manager = RecentProjectsManagerBase.getInstanceEx()
        val invalidPaths = RecentProjectPathSupport.findInvalidLocalPaths(manager.getRecentPaths())
        if (invalidPaths.isEmpty()) {
            notify("No invalid recent projects found.", NotificationType.INFORMATION)
            return
        }

        invalidPaths.forEach(manager::removePath)

        val entryLabel = if (invalidPaths.size == 1) "entry" else "entries"
        notify(
            "Removed ${invalidPaths.size} invalid recent project $entryLabel.",
            NotificationType.INFORMATION,
        )
    }

    private fun findInvalidPaths(): List<String> {
        return RecentProjectPathSupport.findInvalidLocalPaths(
            RecentProjectsManagerBase.getInstanceEx().getRecentPaths(),
        )
    }

    private fun notify(content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Recent Project Cleaner")
            .createNotification("Recent Project Cleaner", content, type)
            .notify(null)
    }
}

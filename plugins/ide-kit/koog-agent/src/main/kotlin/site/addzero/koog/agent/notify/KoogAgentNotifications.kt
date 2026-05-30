package site.addzero.koog.agent.notify

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

internal object KoogAgentNotifications {
    fun error(
        project: Project,
        content: String,
    ) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("IdeKit Notifications")
            .createNotification("ide-kit AI", content, NotificationType.ERROR)
            .notify(project)
    }
}

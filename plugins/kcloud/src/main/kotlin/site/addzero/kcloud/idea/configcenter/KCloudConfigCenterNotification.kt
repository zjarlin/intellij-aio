package site.addzero.kcloud.idea.configcenter

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

internal object KCloudConfigCenterNotification {
    private const val GROUP_ID = "KCloud Config Center"

    fun info(project: Project?, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(content, NotificationType.INFORMATION)
            .notify(project)
    }

    fun error(project: Project?, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(content, NotificationType.ERROR)
            .notify(project)
    }
}

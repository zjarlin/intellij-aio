package com.addzero.addl.util

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

object NotificationUtil {
    private const val NOTIFICATION_GROUP_ID = "AutoDDL Notifications"
    
    fun showError(project: Project?, message: String) {
        project?.let {
            NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                .createNotification(message, NotificationType.ERROR)
                .notify(it)
        }
    }
    
    fun showInfo(project: Project?, message: String) {
        project?.let {
            NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                .createNotification(message, NotificationType.INFORMATION)
                .notify(it)
        }
    }
    
    fun showWarning(project: Project?, message: String) {
        project?.let {
            NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                .createNotification(message, NotificationType.WARNING)
                .notify(it)
        }
    }
} 
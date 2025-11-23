package site.addzero.gradle.favorites.action

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import site.addzero.gradle.favorites.model.FavoriteGradleTask
import site.addzero.gradle.favorites.service.GradleFavoritesService
import site.addzero.gradle.favorites.strategy.EditorContextStrategy

class ShowFavoritesNotificationAction {
    
    fun showNotificationForModule(project: Project, modulePath: String) {
        val service = GradleFavoritesService.getInstance(project)
        val favorites = service.getFavoritesForModule(modulePath)
        
        if (favorites.isEmpty()) return
        
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("Gradle Favorites")
            .createNotification(
                "Gradle Favorites Available",
                "You have ${favorites.size} favorite task(s) for this module",
                NotificationType.INFORMATION
            )
        
        favorites.forEach { task ->
            notification.addAction(createExecuteAction(task))
        }
        
        notification.notify(project)
    }
    
    private fun createExecuteAction(task: FavoriteGradleTask): NotificationAction {
        return object : NotificationAction(task.displayName) {
            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                val project = e.project ?: return
                
                val strategy = EditorContextStrategy()
                try {
                    strategy.executeTask(project, task)
                    notification.expire()
                } catch (ex: Exception) {
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("Gradle Favorites")
                        .createNotification(
                            "Task Failed",
                            "Failed to execute ${task.displayName}: ${ex.message}",
                            NotificationType.ERROR
                        )
                        .notify(project)
                }
            }
        }
    }
}

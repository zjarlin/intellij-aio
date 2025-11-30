package site.addzero.gradle.buddy.favorites

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project

/**
 * 从收藏中移除 Gradle 任务
 */
class RemoveFromFavoritesAction : AnAction(
    "Remove from Favorites",
    "Remove this Gradle task from favorites",
    AllIcons.Actions.GC
), DumbAware {
    
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        
        val taskInfo = GradleTaskExtractor.extractFromEvent(e)
        if (taskInfo == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        
        val service = GradleFavoritesService.getInstance(project)
        val isFavorite = service.isFavorite(taskInfo)
        
        e.presentation.isEnabledAndVisible = isFavorite
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val taskInfo = GradleTaskExtractor.extractFromEvent(e) ?: return
        
        val service = GradleFavoritesService.getInstance(project)
        service.removeFavorite(taskInfo)
        
        showNotification(project, "Removed '${taskInfo.displayName}' from favorites.", NotificationType.INFORMATION)
    }
    
    private fun showNotification(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("GradleBuddy")
            .createNotification(message, type)
            .notify(project)
    }
}

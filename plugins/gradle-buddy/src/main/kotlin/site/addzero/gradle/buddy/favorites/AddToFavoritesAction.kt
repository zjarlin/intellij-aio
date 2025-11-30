package site.addzero.gradle.buddy.favorites

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project

/**
 * 添加 Gradle 任务到收藏
 */
class AddToFavoritesAction : AnAction(
    "Add to Favorites",
    "Add this Gradle task to favorites",
    AllIcons.Nodes.Favorite
), DumbAware {
    
    private val logger = Logger.getInstance(AddToFavoritesAction::class.java)
    
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        
        val taskInfo = GradleTaskExtractor.extractFromEvent(e)
        
        if (taskInfo == null) {
            // 仍然显示，但禁用，方便调试
            e.presentation.isVisible = true
            e.presentation.isEnabled = false
            e.presentation.text = "Add to Favorites (Select a task)"
            return
        }
        
        val service = GradleFavoritesService.getInstance(project)
        val isFavorite = service.isFavorite(taskInfo)
        
        e.presentation.isEnabledAndVisible = true
        e.presentation.text = if (isFavorite) "Already in Favorites ⭐" else "Add to Favorites"
        e.presentation.icon = if (isFavorite) AllIcons.Nodes.Favorite else AllIcons.Actions.AddToDictionary
        e.presentation.isEnabled = !isFavorite
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val taskInfo = GradleTaskExtractor.extractFromEvent(e) ?: return
        
        val service = GradleFavoritesService.getInstance(project)
        
        if (service.isFavorite(taskInfo)) {
            showNotification(project, "Task '${taskInfo.displayName}' is already in favorites.", NotificationType.WARNING)
            return
        }
        
        service.addFavorite(taskInfo)
        showNotification(project, "Added '${taskInfo.displayName}' to favorites! ⭐", NotificationType.INFORMATION)
    }
    
    private fun showNotification(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("GradleBuddy")
            .createNotification(message, type)
            .notify(project)
    }
}

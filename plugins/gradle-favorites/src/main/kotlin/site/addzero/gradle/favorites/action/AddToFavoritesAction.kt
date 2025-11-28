package site.addzero.gradle.favorites.action

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import site.addzero.gradle.favorites.service.GradleFavoritesService
import site.addzero.gradle.favorites.strategy.GradleTaskStrategyRegistry

class AddToFavoritesAction : AnAction(
    "Add to Favorites",
    "Add this Gradle task to favorites",
    AllIcons.Actions.AddToDictionary
) {
    
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        
        val registry = GradleTaskStrategyRegistry.getInstance(project)
        val strategy = registry.findSupportedStrategy(e)
        
        if (strategy == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        
        val taskInfo = strategy.extractTaskInfo(e)
        if (taskInfo == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        
        val service = GradleFavoritesService.getInstance(project)
        val isFavorite = service.isFavorite(taskInfo)
        
        e.presentation.isEnabledAndVisible = true
        e.presentation.text = if (isFavorite) "Already in Favorites" else "Add to Favorites"
        e.presentation.icon = if (isFavorite) AllIcons.Nodes.Favorite else AllIcons.Actions.AddToDictionary
        e.presentation.isEnabled = !isFavorite
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        val registry = GradleTaskStrategyRegistry.getInstance(project)
        val strategy = registry.findSupportedStrategy(e) ?: return
        
        val taskInfo = strategy.extractTaskInfo(e) ?: return
        val service = GradleFavoritesService.getInstance(project)
        
        if (service.isFavorite(taskInfo)) {
            showNotification(project, "Task '${taskInfo.displayName}' is already in favorites.", NotificationType.WARNING)
            return
        }
        
        service.addFavorite(taskInfo)
        showNotification(project, "Added '${taskInfo.displayName}' to favorites! ⭐", NotificationType.INFORMATION)
    }
    
    private fun showNotification(project: com.intellij.openapi.project.Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Gradle Favorites")
            .createNotification(message, type)
            .notify(project)
    }
}

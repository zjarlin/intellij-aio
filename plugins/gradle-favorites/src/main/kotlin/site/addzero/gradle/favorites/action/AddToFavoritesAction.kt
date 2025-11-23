package site.addzero.gradle.favorites.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import site.addzero.gradle.favorites.service.GradleFavoritesService
import site.addzero.gradle.favorites.strategy.GradleTaskStrategyRegistry

class AddToFavoritesAction : AnAction("Add to Favorites") {
    
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        
        val registry = GradleTaskStrategyRegistry.getInstance(project)
        val strategy = registry.findSupportedStrategy(e)
        
        e.presentation.isEnabledAndVisible = strategy != null && strategy.extractTaskInfo(e) != null
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        val registry = GradleTaskStrategyRegistry.getInstance(project)
        val strategy = registry.findSupportedStrategy(e) ?: return
        
        val taskInfo = strategy.extractTaskInfo(e) ?: return
        val service = GradleFavoritesService.getInstance(project)
        
        if (service.isFavorite(taskInfo)) {
            Messages.showInfoMessage(
                project,
                "Task '${taskInfo.displayName}' is already in favorites.",
                "Already Favorited"
            )
            return
        }
        
        service.addFavorite(taskInfo)
        Messages.showInfoMessage(
            project,
            "Added '${taskInfo.displayName}' to favorites.",
            "Task Favorited"
        )
    }
}

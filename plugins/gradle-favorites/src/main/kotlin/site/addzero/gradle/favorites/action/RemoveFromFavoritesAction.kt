package site.addzero.gradle.favorites.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import site.addzero.gradle.favorites.service.GradleFavoritesService
import site.addzero.gradle.favorites.strategy.GradleTaskStrategyRegistry

class RemoveFromFavoritesAction : AnAction("Remove from Favorites") {
    
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        
        val registry = GradleTaskStrategyRegistry.getInstance(project)
        val strategy = registry.findSupportedStrategy(e)
        val taskInfo = strategy?.extractTaskInfo(e)
        
        if (taskInfo == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        
        val service = GradleFavoritesService.getInstance(project)
        e.presentation.isEnabledAndVisible = service.isFavorite(taskInfo)
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        val registry = GradleTaskStrategyRegistry.getInstance(project)
        val strategy = registry.findSupportedStrategy(e) ?: return
        
        val taskInfo = strategy.extractTaskInfo(e) ?: return
        val service = GradleFavoritesService.getInstance(project)
        
        service.removeFavorite(taskInfo)
        Messages.showInfoMessage(
            project,
            "Removed '${taskInfo.displayName}' from favorites.",
            "Task Removed"
        )
    }
}

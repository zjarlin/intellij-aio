package site.addzero.gradle.favorites.action

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import site.addzero.gradle.favorites.service.GradleFavoritesService
import site.addzero.gradle.favorites.strategy.GradleTaskStrategyRegistry

class ShowFavoritesMenuAction : ActionGroup("Gradle Favorites", "Execute favorite Gradle tasks", null) {
    
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        
        val registry = GradleTaskStrategyRegistry.getInstance(project)
        val strategy = registry.findSupportedStrategy(e)
        val modulePath = strategy?.getCurrentModulePath(e)
        
        if (modulePath == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        
        val service = GradleFavoritesService.getInstance(project)
        val favorites = service.getFavoritesForModule(modulePath)
        
        e.presentation.isEnabledAndVisible = favorites.isNotEmpty()
    }
    
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        if (e == null) return emptyArray()
        
        val project = e.project ?: return emptyArray()
        
        val registry = GradleTaskStrategyRegistry.getInstance(project)
        val strategy = registry.findSupportedStrategy(e) ?: return emptyArray()
        val modulePath = strategy.getCurrentModulePath(e) ?: return emptyArray()
        
        val service = GradleFavoritesService.getInstance(project)
        val favorites = service.getFavoritesForModule(modulePath)
        
        return favorites
            .map { ExecuteFavoriteTaskAction(it) }
            .toTypedArray()
    }
}

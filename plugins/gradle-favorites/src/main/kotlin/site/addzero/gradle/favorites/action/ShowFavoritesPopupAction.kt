package site.addzero.gradle.favorites.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import site.addzero.gradle.favorites.model.FavoriteGradleTask
import site.addzero.gradle.favorites.service.GradleFavoritesService
import site.addzero.gradle.favorites.strategy.EditorContextStrategy

class ShowFavoritesPopupAction : AnAction("Show Gradle Favorites") {
    
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = GradleFavoritesService.getInstance(project)
        val strategy = EditorContextStrategy()
        
        val currentModulePath = strategy.getCurrentModulePath(e)
        val allFavorites = service.getAllFavorites()
        
        val tasksToShow = currentModulePath
            ?.let { path -> allFavorites.filter { it.matchesModule(path) } }
            ?.takeIf { it.isNotEmpty() }
            ?: allFavorites
        
        if (tasksToShow.isEmpty()) {
            JBPopupFactory.getInstance()
                .createMessage("No favorite tasks found")
                .showInBestPositionFor(e.dataContext)
            return
        }
        
        val groupedTasks = tasksToShow.groupBy { it.group }.toSortedMap()
        
        if (groupedTasks.size == 1 && groupedTasks.containsKey("Default")) {
            val popup = JBPopupFactory.getInstance().createListPopup(
                FavoritesPopupStep(project, tasksToShow, strategy)
            )
            popup.showInBestPositionFor(e.dataContext)
        } else {
            val popup = JBPopupFactory.getInstance().createListPopup(
                GroupPopupStep(project, groupedTasks, strategy)
            )
            popup.showInBestPositionFor(e.dataContext)
        }
    }
    
    private class GroupPopupStep(
        private val project: com.intellij.openapi.project.Project,
        private val groupedTasks: Map<String, List<FavoriteGradleTask>>,
        private val strategy: EditorContextStrategy
    ) : BaseListPopupStep<String>("Gradle Favorites", groupedTasks.keys.toList()) {
        
        override fun getTextFor(value: String): String = value
        
        override fun hasSubstep(selectedValue: String): Boolean = true
        
        override fun onChosen(selectedValue: String, finalChoice: Boolean): PopupStep<*>? {
            val tasks = groupedTasks[selectedValue] ?: return null
            return FavoritesPopupStep(project, tasks, strategy)
        }
    }
    
    private class FavoritesPopupStep(
        private val project: com.intellij.openapi.project.Project,
        tasks: List<FavoriteGradleTask>,
        private val strategy: EditorContextStrategy
    ) : BaseListPopupStep<FavoriteGradleTask>("Tasks", tasks) {
        
        override fun getTextFor(value: FavoriteGradleTask): String = value.displayName
        
        override fun onChosen(selectedValue: FavoriteGradleTask, finalChoice: Boolean): PopupStep<*>? {
            if (finalChoice) {
                strategy.executeTask(project, selectedValue)
            }
            return null
        }
    }
}

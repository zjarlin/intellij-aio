package site.addzero.gradle.favorites.service

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import site.addzero.gradle.favorites.model.FavoriteGradleTask
import site.addzero.gradle.favorites.model.FavoriteTaskData
import site.addzero.gradle.favorites.model.FavoriteTasksState

@Service(Service.Level.PROJECT)
@State(
    name = "GradleFavoritesService",
    storages = [Storage("gradleFavorites.xml")]
)
class GradleFavoritesService : PersistentStateComponent<FavoriteTasksState> {
    
    private var state = FavoriteTasksState()
    
    override fun getState(): FavoriteTasksState = state
    
    override fun loadState(state: FavoriteTasksState) {
        this.state = state
    }
    
    fun getAllFavorites(): List<FavoriteGradleTask> = 
        state.favorites.mapNotNull { it.toFavoriteTask() }
    
    fun getFavoritesForModule(modulePath: String): List<FavoriteGradleTask> =
        getAllFavorites().filter { it.matchesModule(modulePath) }
    
    fun addFavorite(task: FavoriteGradleTask) {
        if (isFavorite(task)) return
        state.favorites.add(FavoriteTaskData.from(task))
    }
    
    fun removeFavorite(task: FavoriteGradleTask) {
        state.favorites.removeIf { 
            it.projectPath == task.projectPath && it.taskName == task.taskName 
        }
    }
    
    fun isFavorite(task: FavoriteGradleTask): Boolean =
        getAllFavorites().any { 
            it.projectPath == task.projectPath && it.taskName == task.taskName 
        }
    
    fun clearAll() {
        state.favorites.clear()
    }
    
    companion object {
        fun getInstance(project: Project): GradleFavoritesService =
            project.getService(GradleFavoritesService::class.java)
    }
}

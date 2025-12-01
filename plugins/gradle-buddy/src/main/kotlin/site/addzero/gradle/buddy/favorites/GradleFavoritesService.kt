package site.addzero.gradle.buddy.favorites

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Gradle 收藏任务服务
 */
@Service(Service.Level.PROJECT)
@State(
    name = "GradleFavoritesService",
    storages = [Storage("gradleBuddyFavorites.xml")]
)
class GradleFavoritesService : PersistentStateComponent<GradleFavoritesService.State> {

    data class State(
        var favorites: MutableList<FavoriteTaskState> = mutableListOf()
    )

    data class FavoriteTaskState(
        var projectPath: String = "",
        var taskName: String = "",
        var group: String = "other",
        var order: Int = 0
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }

    fun getFavorites(): List<FavoriteGradleTask> {
        return myState.favorites.map {
            FavoriteGradleTask(it.projectPath, it.taskName, it.group, it.order)
        }.sortedBy { it.order }
    }

    fun addFavorite(task: FavoriteGradleTask) {
        if (!isFavorite(task)) {
            val maxOrder = myState.favorites.maxOfOrNull { it.order } ?: 0
            myState.favorites.add(FavoriteTaskState(
                projectPath = task.projectPath,
                taskName = task.taskName,
                group = task.group,
                order = maxOrder + 1
            ))
        }
    }

    fun removeFavorite(task: FavoriteGradleTask) {
        myState.favorites.removeIf {
            it.projectPath == task.projectPath && it.taskName == task.taskName
        }
    }

    fun isFavorite(task: FavoriteGradleTask): Boolean {
        return myState.favorites.any {
            it.projectPath == task.projectPath && it.taskName == task.taskName
        }
    }

    fun clearAll() {
        myState.favorites.clear()
    }

    companion object {
        fun getInstance(project: Project): GradleFavoritesService {
            return project.service()
        }
    }
}

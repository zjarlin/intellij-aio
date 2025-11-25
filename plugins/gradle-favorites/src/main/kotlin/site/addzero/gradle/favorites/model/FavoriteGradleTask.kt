package site.addzero.gradle.favorites.model

import com.intellij.openapi.components.BaseState

data class FavoriteGradleTask(
    val projectPath: String,
    val taskName: String,
    val displayName: String = "$projectPath:$taskName",
    val group: String = "Default",
    val order: Int = 0
) {
    fun toExecutableCommand(): String = "$projectPath:$taskName"
    
    fun matchesModule(modulePath: String): Boolean = 
        projectPath == modulePath || projectPath.startsWith("$modulePath:")
}

class FavoriteTasksState : BaseState() {
    var favorites by list<FavoriteTaskData>()
}

class FavoriteTaskData : BaseState() {
    var projectPath by string()
    var taskName by string()
    var displayName by string()
    var group by string()
    var order by property(0)
    
    fun toFavoriteTask(): FavoriteGradleTask? {
        val path = projectPath ?: return null
        val name = taskName ?: return null
        return FavoriteGradleTask(
            projectPath = path,
            taskName = name,
            displayName = displayName ?: "$path:$name",
            group = group ?: "Default",
            order = order
        )
    }
    
    companion object {
        fun from(task: FavoriteGradleTask) = FavoriteTaskData().apply {
            projectPath = task.projectPath
            taskName = task.taskName
            displayName = task.displayName
            group = task.group
            order = task.order
        }
    }
}

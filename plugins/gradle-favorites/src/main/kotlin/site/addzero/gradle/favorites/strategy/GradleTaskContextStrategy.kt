package site.addzero.gradle.favorites.strategy

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import site.addzero.gradle.favorites.model.FavoriteGradleTask

interface GradleTaskContextStrategy {
    fun support(event: AnActionEvent): Boolean
    
    fun extractTaskInfo(event: AnActionEvent): FavoriteGradleTask?
    
    fun getCurrentModulePath(event: AnActionEvent): String?
    
    fun executeTask(project: Project, task: FavoriteGradleTask)
}

abstract class AbstractGradleTaskContextStrategy : GradleTaskContextStrategy {
    
    override fun executeTask(project: Project, task: FavoriteGradleTask) {
        val externalSystemTaskManager = try {
            Class.forName("org.jetbrains.plugins.gradle.util.GradleUtil")
                .getDeclaredMethod("runTask", 
                    Project::class.java, 
                    String::class.java, 
                    String::class.java)
        } catch (e: Exception) {
            null
        }
        
        if (externalSystemTaskManager != null) {
            try {
                externalSystemTaskManager.invoke(null, project, task.toExecutableCommand(), project.basePath)
            } catch (e: Exception) {
                executeTaskFallback(project, task)
            }
        } else {
            executeTaskFallback(project, task)
        }
    }
    
    private fun executeTaskFallback(project: Project, task: FavoriteGradleTask) {
        val command = "./gradlew ${task.toExecutableCommand()}"
        val processBuilder = ProcessBuilder(command.split(" "))
            .directory(java.io.File(project.basePath ?: "."))
            .redirectErrorStream(true)
        
        processBuilder.start()
    }
}

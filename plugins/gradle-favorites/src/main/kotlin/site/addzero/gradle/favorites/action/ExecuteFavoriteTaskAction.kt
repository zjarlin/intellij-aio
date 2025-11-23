package site.addzero.gradle.favorites.action

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import site.addzero.gradle.favorites.model.FavoriteGradleTask
import site.addzero.gradle.favorites.strategy.GradleTaskStrategyRegistry

class ExecuteFavoriteTaskAction(
    private val task: FavoriteGradleTask
) : AnAction(task.displayName) {
    
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        val registry = GradleTaskStrategyRegistry.getInstance(project)
        val strategy = registry.findSupportedStrategy(e) ?: return
        
        notifyTaskExecution(project, task)
        
        try {
            strategy.executeTask(project, task)
        } catch (ex: Exception) {
            notifyTaskError(project, task, ex)
        }
    }
    
    private fun notifyTaskExecution(project: Project, task: FavoriteGradleTask) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Gradle Favorites")
            .createNotification(
                "Executing Gradle Task",
                "Running: ${task.displayName}",
                NotificationType.INFORMATION
            )
            .notify(project)
    }
    
    private fun notifyTaskError(project: Project, task: FavoriteGradleTask, error: Exception) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Gradle Favorites")
            .createNotification(
                "Task Execution Failed",
                "Failed to run ${task.displayName}: ${error.message}",
                NotificationType.ERROR
            )
            .notify(project)
    }
}

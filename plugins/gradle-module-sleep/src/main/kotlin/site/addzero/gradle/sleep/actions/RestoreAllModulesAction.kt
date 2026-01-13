package site.addzero.gradle.sleep.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import site.addzero.gradle.sleep.loader.OnDemandModuleLoader

/**
 * Action: 恢复所有被 Gradle Module Sleep 排除的模块
 */
class RestoreAllModulesAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val success = OnDemandModuleLoader.restoreAllModules(project, syncAfter = true)

        if (success) {
            showNotification(
                project,
                "All Modules Restored",
                "All excluded modules have been restored. Gradle sync triggered.",
                NotificationType.INFORMATION
            )
        } else {
            showNotification(
                project,
                "Restore Failed",
                "Failed to restore modules. Check the IDE log for details.",
                NotificationType.ERROR
            )
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    private fun showNotification(
        project: Project,
        title: String,
        content: String,
        type: NotificationType
    ) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("GradleModuleSleep")
            .createNotification(title, content, type)
            .notify(project)
    }
}

package site.addzero.gradle.buddy.ondemand

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

/**
 * Action: 只加载当前打开标签页对应的模块
 * 流程: 获取打开的标签页 -> 推导模块 -> 生成 include 语句 -> 应用到 settings.gradle.kts -> 触发 Gradle 同步
 */
class LoadOnlyOpenTabModulesAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val result = OnDemandModuleLoader.loadOnlyOpenTabModules(project)
        
        when (result) {
            is LoadResult.Success -> {
                showNotification(
                    project,
                    "On-Demand Loading Applied",
                    "Loaded ${result.modules.size} modules:\n${result.modules.sorted().joinToString("\n")}",
                    NotificationType.INFORMATION
                )
            }
            is LoadResult.NoOpenFiles -> {
                showNotification(
                    project,
                    "No Open Files",
                    "Please open some files first to detect required modules.",
                    NotificationType.WARNING
                )
            }
            is LoadResult.NoModulesDetected -> {
                showNotification(
                    project,
                    "No Modules Detected",
                    "Could not detect any Gradle modules from ${result.openFileCount} open files.",
                    NotificationType.WARNING
                )
            }
            is LoadResult.Failed -> {
                showNotification(
                    project,
                    "Load Failed",
                    result.reason,
                    NotificationType.ERROR
                )
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    private fun showNotification(
        project: com.intellij.openapi.project.Project,
        title: String,
        content: String,
        type: NotificationType
    ) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("GradleBuddy")
            .createNotification(title, content, type)
            .notify(project)
    }
}

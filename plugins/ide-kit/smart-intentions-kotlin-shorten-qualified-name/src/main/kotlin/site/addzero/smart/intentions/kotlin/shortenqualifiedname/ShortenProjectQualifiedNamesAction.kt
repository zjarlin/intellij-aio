package site.addzero.smart.intentions.kotlin.shortenqualifiedname

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import site.addzero.smart.intentions.core.SmartIntentionsMessages

class ShortenProjectQualifiedNamesAction : AnAction(
    SmartIntentionsMessages.SHORTEN_PROJECT_QUALIFIED_NAMES,
    "将项目 Kotlin 文件中的可导入全限定名缩短为 import 加短名",
    null,
) {
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        ProjectShortenQualifiedNameSupport.applyInBackground(project) { result ->
            notify(project, result)
        }
    }

    private fun notify(project: Project, result: ProjectShortenQualifiedNameResult) {
        val content = when {
            result.scannedFiles == 0 -> "未找到 Kotlin 文件"
            result.changedFiles == 0 -> "已扫描 ${result.scannedFiles} 个 Kotlin 文件，没有可缩短的全限定名"
            else -> "已扫描 ${result.scannedFiles} 个 Kotlin 文件，缩短 ${result.changedFiles} 个文件中的全限定名"
        }
        NotificationGroupManager.getInstance()
            .getNotificationGroup("IdeKit Notifications")
            .createNotification(content, NotificationType.INFORMATION)
            .notify(project)
    }
}

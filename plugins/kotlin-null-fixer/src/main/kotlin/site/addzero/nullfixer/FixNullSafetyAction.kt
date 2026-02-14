package site.addzero.nullfixer

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.EditorNotifications
import org.jetbrains.kotlin.psi.KtFile

/**
 * 一键修复当前 Kotlin 文件中所有空安全相关错误。
 * 入口：右键菜单 / Tools 菜单
 */
class FixNullSafetyAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.getData(CommonDataKeys.PSI_FILE) is KtFile
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) as? KtFile ?: return

        val result = NullSafetyFixer.fixFile(project, psiFile)

        val message = when {
            result.fixed == 0 && result.skipped == 0 -> "✅ 未发现空安全问题"
            result.fixed > 0 && result.skipped == 0 -> "✅ 已修复 ${result.fixed} 处"
            result.fixed > 0 -> "✅ 修复 ${result.fixed} 处，跳过 ${result.skipped} 处"
            else -> "⚠️ 发现 ${result.skipped} 处问题但无法自动定位"
        }

        NotificationGroupManager.getInstance()
            .getNotificationGroup("KotlinNullFixer")
            .createNotification("Kotlin Null Fixer", message, NotificationType.INFORMATION)
            .notify(project)

        // 刷新横幅
        val vf = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (vf != null) {
            EditorNotifications.getInstance(project).updateNotifications(vf)
        }
    }
}

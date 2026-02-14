package site.addzero.java.nullfixer

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiJavaFile
import com.intellij.ui.EditorNotifications

/**
 * 一键批量修复当前 Java 文件中所有空指针相关警告。
 * 调用 IntelliJ 内置的 Quick Fix（Surround with null check 等）。
 */
class FixJavaNullSafetyAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.getData(CommonDataKeys.PSI_FILE) is PsiJavaFile
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) as? PsiJavaFile ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return

        val result = JavaNullSafetyFixer.fixFile(project, psiFile, editor)

        val message = when {
            result.fixed == 0 && result.skipped == 0 -> "✅ 未发现空指针问题"
            result.fixed > 0 && result.skipped == 0 -> "✅ 已修复 ${result.fixed} 处"
            result.fixed > 0 -> "✅ 修复 ${result.fixed} 处，跳过 ${result.skipped} 处"
            else -> "⚠️ 发现 ${result.skipped} 处问题但无法自动修复"
        }

        NotificationGroupManager.getInstance()
            .getNotificationGroup("JavaNullFixer")
            .createNotification("Java Null Fixer", message, NotificationType.INFORMATION)
            .notify(project)

        val vf = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (vf != null) {
            EditorNotifications.getInstance(project).updateNotifications(vf)
        }
    }
}

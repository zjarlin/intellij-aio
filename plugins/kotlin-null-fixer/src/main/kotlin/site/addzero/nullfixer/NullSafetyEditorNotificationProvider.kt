package site.addzero.nullfixer

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import org.jetbrains.kotlin.psi.KtFile
import java.util.function.Function
import javax.swing.JComponent

/**
 * 编辑器顶部横幅：当 .kt 文件存在空安全错误时，显示一键修复横幅。
 *
 * 与 FloatingToolbarProvider 不同，EditorNotificationProvider 会在以下时机自动刷新：
 * - 文件打开时
 * - 文件内容变化后（通过 [NullSafetyDaemonListener] 在高亮完成后触发 updateNotifications）
 * - 手动调用 EditorNotifications.updateNotifications()
 *
 * 这样就能实现：有错误时显示横幅，没错误时自动消失。
 */
class NullSafetyEditorNotificationProvider : EditorNotificationProvider, DumbAware {

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<in FileEditor, out JComponent?> {
        // 只处理 .kt / .kts 文件
        if (!file.name.endsWith(".kt") && !file.name.endsWith(".kts")) {
            return Function { null }
        }

        return Function { fileEditor ->
            if (fileEditor !is TextEditor) return@Function null
            val editor = fileEditor.editor
            val document = editor.document

            // 检查是否有可修复的空安全错误（匹配逻辑与 NullSafetyFixer.collectErrors 一致）
            var errorCount = 0
            DaemonCodeAnalyzerEx.processHighlights(
                document, project, HighlightSeverity.ERROR, 0, document.textLength
            ) { info: HighlightInfo ->
                val desc = info.description ?: ""
                if (NullSafetyFixer.isFixableError(desc)) {
                    errorCount++
                }
                true
            }

            if (errorCount == 0) return@Function null

            // 创建横幅
            EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning).apply {
                text = "发现 $errorCount 处空安全问题可自动修复"
                createActionLabel("一键修复 → ?.") {
                    val psiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(file)
                    if (psiFile is KtFile) {
                        val result = NullSafetyFixer.fixFile(project, psiFile)
                        if (result.fixed > 0) {
                            com.intellij.notification.NotificationGroupManager.getInstance()
                                .getNotificationGroup("KotlinNullFixer")
                                .createNotification(
                                    "Kotlin Null Fixer",
                                    "✅ 已修复 ${result.fixed} 处",
                                    com.intellij.notification.NotificationType.INFORMATION
                                ).notify(project)
                        }
                    }
                    // 修复后刷新横幅（会重新检查，如果没错误了就消失）
                    com.intellij.ui.EditorNotifications.getInstance(project).updateNotifications(file)
                }
                createActionLabel("忽略") {
                    com.intellij.ui.EditorNotifications.getInstance(project).updateNotifications(file)
                }
            }
        }
    }
}

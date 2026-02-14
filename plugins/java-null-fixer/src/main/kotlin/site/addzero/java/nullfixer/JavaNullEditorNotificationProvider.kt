package site.addzero.java.nullfixer

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import java.util.function.Function
import javax.swing.JComponent

/**
 * 编辑器顶部横幅：当 .java 文件存在空指针警告时，显示一键修复横幅。
 */
class JavaNullEditorNotificationProvider : EditorNotificationProvider, DumbAware {

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<in FileEditor, out JComponent?> {
        if (!file.name.endsWith(".java")) return Function { null }

        return Function { fileEditor ->
            if (fileEditor !is TextEditor) return@Function null
            val editor = fileEditor.editor
            val document = editor.document

            var errorCount = 0
            for (severity in listOf(HighlightSeverity.WARNING, HighlightSeverity.ERROR)) {
                DaemonCodeAnalyzerEx.processHighlights(
                    document, project, severity, 0, document.textLength
                ) { info: HighlightInfo ->
                    val desc = info.description ?: ""
                    if (JavaNullSafetyFixer.NULL_WARNING_PATTERNS.any { desc.contains(it, ignoreCase = true) }) {
                        errorCount++
                    }
                    true
                }
            }

            if (errorCount == 0) return@Function null

            EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning).apply {
                text = "发现 $errorCount 处空指针问题可自动修复"
                createActionLabel("一键修复 (Null Check)") {
                    val psiFile = PsiManager.getInstance(project).findFile(file)
                    if (psiFile is PsiJavaFile) {
                        val result = JavaNullSafetyFixer.fixFile(project, psiFile, editor)
                        if (result.fixed > 0) {
                            NotificationGroupManager.getInstance()
                                .getNotificationGroup("JavaNullFixer")
                                .createNotification(
                                    "Java Null Fixer",
                                    "✅ 已修复 ${result.fixed} 处",
                                    NotificationType.INFORMATION
                                ).notify(project)
                        }
                    }
                    EditorNotifications.getInstance(project).updateNotifications(file)
                }
                createActionLabel("忽略") {
                    EditorNotifications.getInstance(project).updateNotifications(file)
                }
            }
        }
    }
}

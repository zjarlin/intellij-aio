package site.addzero.nullfixer

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.ui.EditorNotifications

/**
 * 监听 DaemonCodeAnalyzer 完成高亮，刷新编辑器顶部横幅。
 * 这样当文件编辑后产生/消除空安全错误时，横幅会自动出现/消失。
 */
class NullSafetyDaemonListener : ProjectActivity {

    override suspend fun execute(project: Project) {
        project.messageBus.connect().subscribe(
            DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC,
            object : DaemonCodeAnalyzer.DaemonListener {
                override fun daemonFinished() {
                    val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
                    val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
                    if (!file.name.endsWith(".kt") && !file.name.endsWith(".kts")) return
                    // 刷新横幅，让 EditorNotificationProvider 重新判断
                    EditorNotifications.getInstance(project).updateNotifications(file)
                }
            }
        )
    }
}

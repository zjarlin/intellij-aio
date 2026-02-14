package site.addzero.java.nullfixer

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.ui.EditorNotifications

/**
 * 监听 DaemonCodeAnalyzer 完成高亮，刷新编辑器顶部横幅。
 */
class JavaNullDaemonListener : ProjectActivity {

    override suspend fun execute(project: Project) {
        project.messageBus.connect().subscribe(
            DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC,
            object : DaemonCodeAnalyzer.DaemonListener {
                override fun daemonFinished() {
                    val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
                    val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
                    if (!file.name.endsWith(".java")) return
                    EditorNotifications.getInstance(project).updateNotifications(file)
                }
            }
        )
    }
}

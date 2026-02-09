package site.addzero.gradle.buddy.linemarker

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiManager
import com.intellij.util.FileContentUtil

/**
 * Action shown in the line marker popup menu.
 * Allows marking/unmarking an artifact as deprecated with a custom message.
 * After toggling, triggers a daemon restart to refresh gutter icons.
 */
class DeprecateArtifactAction(
    private val group: String,
    private val artifact: String
) : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = DeprecatedArtifactService.getInstance()

        if (service.isDeprecated(group, artifact)) {
            val result = Messages.showYesNoDialog(
                project,
                "「$group:$artifact」已标记为弃用。\n\n弃用信息: ${service.getEntry(group, artifact)?.message ?: "(无)"}\n\n是否取消弃用标记？",
                "取消弃用",
                Messages.getQuestionIcon()
            )
            if (result == Messages.YES) {
                service.undeprecate(group, artifact)
                refreshGutterIcons(e)
            }
        } else {
            val message = Messages.showInputDialog(
                project,
                "请输入弃用原因 (可选):",
                "标记弃用: $group:$artifact",
                Messages.getWarningIcon(),
                "",
                null
            )
            if (message != null) {
                service.deprecate(group, artifact, message)
                refreshGutterIcons(e)
            }
        }
    }

    private fun refreshGutterIcons(e: AnActionEvent) {
        val project = e.project ?: return
        val vFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
            ?: e.getData(CommonDataKeys.EDITOR)?.virtualFile

        if (vFile != null) {
            // Reparse the file to trigger re-analysis (including gutter icon refresh)
            FileContentUtil.reparseFiles(project, listOf(vFile), true)
        } else {
            // Fallback: reparse all open files
            val openFiles = FileEditorManager.getInstance(project).openFiles.toList()
            if (openFiles.isNotEmpty()) {
                FileContentUtil.reparseFiles(project, openFiles, true)
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val service = DeprecatedArtifactService.getInstance()
        val deprecated = service.isDeprecated(group, artifact)
        e.presentation.text = if (deprecated) "取消弃用 $group:$artifact" else "标记为弃用"
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

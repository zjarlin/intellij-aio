package site.addzero.dioxus.buddy.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import site.addzero.dioxus.buddy.model.DioxusPreviewResolver
import site.addzero.dioxus.buddy.runner.DioxusPreviewRunner

class OpenDioxusPreviewAction : AnAction(
    "Open Dioxus Preview",
    "Run a local Dioxus web preview for the macro-marked Rust function",
    AllIcons.Actions.PreviewDetails,
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val target = if (editor != null && file != null) {
            DioxusPreviewResolver.resolveAtEditor(project, editor, file)
        } else {
            DioxusPreviewResolver.resolveFromCurrentEditor(project)
        } ?: return

        DioxusPreviewRunner.run(project, target)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val target = if (project != null && editor != null && file != null) {
            DioxusPreviewResolver.resolveAtEditor(project, editor, file)
        } else {
            null
        }

        e.presentation.isEnabledAndVisible = project != null && target != null
        e.presentation.text = target?.let { "Open Dioxus Preview: ${it.functionName}" }
            ?: "Open Dioxus Preview"
        e.presentation.description = target?.let {
            "Run a local Dioxus preview for ${it.displayName}"
        } ?: "Run a local Dioxus preview for the current Rust function"
    }
}

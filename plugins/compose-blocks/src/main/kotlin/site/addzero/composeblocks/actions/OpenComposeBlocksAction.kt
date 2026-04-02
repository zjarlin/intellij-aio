package site.addzero.composeblocks.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import site.addzero.composeblocks.editor.ComposeBlocksFileEditorProvider
import site.addzero.composeblocks.editor.isComposeKotlinFile

class OpenComposeBlocksAction : DumbAwareAction(
    "Open Compose Blocks View",
    "Open the block-based Compose editor",
    AllIcons.Actions.PreviewDetails,
) {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        if (!file.isComposeKotlinFile(project)) {
            return
        }

        ComposeBlocksFileEditorProvider.openComposeBlocks(project, file)
    }

    override fun update(event: AnActionEvent) {
        val project = event.project
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE)
        val enabled = project != null && file != null && file.isComposeKotlinFile(project)
        event.presentation.isEnabledAndVisible = enabled
    }
}

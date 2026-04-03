package site.addzero.composeblocks.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import site.addzero.composeblocks.model.ComposeBlocksMode
import java.util.function.Function
import javax.swing.JComponent

class ComposeBlocksEditorNotificationProvider : EditorNotificationProvider, DumbAware {

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile,
    ): Function<in FileEditor, out JComponent?> {
        if (!file.isComposeKotlinFile(project)) {
            return Function { null }
        }

        return Function { fileEditor ->
            if (fileEditor !is TextEditor) {
                return@Function null
            }

            val mode = file.composeBlocksMode(project) ?: ComposeBlocksMode.INSPECT
            EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info).apply {
                text = when (mode) {
                    ComposeBlocksMode.INSPECT ->
                        "Compose Blocks Inspect Mode keeps blocks and real source in one pane with coupled highlights."

                    ComposeBlocksMode.BUILDER ->
                        "Compose Blocks Builder Mode edits this managed file through palette, canvas, and inspector controls."
                }
                createActionLabel("Open Blocks View") {
                    ComposeBlocksFileEditorProvider.openComposeBlocks(project, file)
                }
            }
        }
    }
}

package site.addzero.composeblocks.editor

import com.intellij.openapi.components.service
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

            project.service<ComposeBlocksTextEditorService>().installIfNeeded(file, fileEditor)
            val mode = file.defaultComposeBlocksMode(project) ?: ComposeBlocksMode.INSPECT
            EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info).apply {
                text = when (mode) {
                    ComposeBlocksMode.INSPECT ->
                        "Open Compose Blocks for a split block browser and live source editor."

                    ComposeBlocksMode.BUILDER ->
                        "Open Compose Blocks Builder for palette, canvas, and named-slot layout editing."

                    ComposeBlocksMode.TEXT ->
                        "Open Compose Blocks views from the integrated editor toolbar."
                }
                createActionLabel("Open Blocks View") {
                    ComposeBlocksFileEditorProvider.openComposeBlocks(project, file, ComposeBlocksMode.INSPECT)
                }
            }
        }
    }
}

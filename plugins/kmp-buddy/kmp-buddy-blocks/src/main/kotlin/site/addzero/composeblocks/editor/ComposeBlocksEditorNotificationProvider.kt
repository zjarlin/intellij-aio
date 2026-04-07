package site.addzero.composeblocks.editor

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationProvider
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

            if (!ComposeBlocksFileEditorProvider.isEnabledByDefault()) {
                return@Function null
            }

            project.service<ComposeBlocksTextEditorService>().installIfNeeded(file, fileEditor)
            null
        }
    }
}

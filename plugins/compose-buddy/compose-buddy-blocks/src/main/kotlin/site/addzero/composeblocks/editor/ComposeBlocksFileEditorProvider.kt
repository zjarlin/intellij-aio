package site.addzero.composeblocks.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.WeighedFileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import site.addzero.composeblocks.model.ComposeBlocksMode

class ComposeBlocksFileEditorProvider : WeighedFileEditorProvider(), DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file.isComposeKotlinFile(project)
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return ComposeBlocksUnifiedFileEditor(project, file)
    }

    override fun getEditorTypeId(): String {
        return EDITOR_TYPE_ID
    }

    override fun getPolicy(): FileEditorPolicy {
        return FileEditorPolicy.HIDE_DEFAULT_EDITOR
    }

    override fun getWeight(): Double {
        return 0.8
    }

    companion object {
        const val EDITOR_TYPE_ID = "compose-blocks-editor"

        fun openComposeBlocks(project: Project, file: VirtualFile) {
            val mode = file.defaultComposeBlocksMode(project) ?: return
            openComposeBlocks(project, file, mode)
        }

        fun openComposeBlocks(
            project: Project,
            file: VirtualFile,
            mode: ComposeBlocksMode,
        ) {
            file.setSelectedComposeBlocksMode(mode)
            val manager = FileEditorManager.getInstance(project)
            manager.openFile(file, true)
            manager.getEditors(file)
                .filterIsInstance<ComposeBlocksUnifiedFileEditor>()
                .forEach { editor ->
                    editor.selectMode(mode, requestFocus = false)
                }
            ApplicationManager.getApplication().invokeLater(
                {
                    if (project.isDisposed) {
                        return@invokeLater
                    }
                    manager.getEditors(file)
                        .filterIsInstance<ComposeBlocksUnifiedFileEditor>()
                        .forEach { editor ->
                            editor.selectMode(mode, requestFocus = false)
                        }
                },
                ModalityState.defaultModalityState(),
            )
        }
    }
}

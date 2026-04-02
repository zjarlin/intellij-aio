package site.addzero.composeblocks.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.WeighedFileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class ComposeBlocksFileEditorProvider : WeighedFileEditorProvider(), DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file.isComposeKotlinFile(project)
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return ComposeBlocksFileEditor(project, file)
    }

    override fun getEditorTypeId(): String {
        return EDITOR_TYPE_ID
    }

    override fun getPolicy(): FileEditorPolicy {
        return FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR
    }

    override fun getWeight(): Double {
        return 0.8
    }

    companion object {
        const val EDITOR_TYPE_ID = "compose-blocks-editor"

        fun openComposeBlocks(project: Project, file: VirtualFile) {
            val manager = FileEditorManager.getInstance(project)
            manager.openFile(file, true)
            manager.setSelectedEditor(file, EDITOR_TYPE_ID)
        }
    }
}

package site.addzero.composeblocks.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.WeighedFileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotifications
import site.addzero.composeblocks.model.ComposeBlocksMode
import site.addzero.composeblocks.settings.ComposeBlocksSettingsService

class ComposeBlocksFileEditorProvider : WeighedFileEditorProvider(), DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file.isComposeKotlinFile(project) && (isEnabledByDefault() || file.getUserData(FORCE_OPEN_KEY) == true)
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return ComposeBlocksUnifiedFileEditor(project, file)
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
        private val FORCE_OPEN_KEY = Key.create<Boolean>("compose.blocks.force.open")

        fun isEnabledByDefault(): Boolean {
            return ComposeBlocksSettingsService.getInstance().state.enableComposeBlocksEditorByDefault
        }

        fun openComposeBlocks(project: Project, file: VirtualFile) {
            val mode = file.defaultComposeBlocksMode(project) ?: return
            openComposeBlocks(project, file, mode)
        }

        fun openComposeBlocks(
            project: Project,
            file: VirtualFile,
            mode: ComposeBlocksMode,
        ) {
            val forceOpen = !isEnabledByDefault()
            file.setSelectedComposeBlocksMode(mode)
            if (forceOpen) {
                file.putUserData(FORCE_OPEN_KEY, true)
            }
            val manager = FileEditorManager.getInstance(project)
            manager.openFile(file, true)
            if (manager.getEditors(file).none { editor -> editor is ComposeBlocksUnifiedFileEditor }) {
                manager.closeFile(file)
                manager.openFile(file, true)
            }
            manager.setSelectedEditor(file, EDITOR_TYPE_ID)
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
                    manager.setSelectedEditor(file, EDITOR_TYPE_ID)
                    if (forceOpen) {
                        file.putUserData(FORCE_OPEN_KEY, null)
                    }
                },
                ModalityState.defaultModalityState(),
            )
        }

        fun handleSettingsChanged() {
            val enabled = isEnabledByDefault()
            ProjectManager.getInstance().openProjects.forEach { project ->
                val manager = FileEditorManager.getInstance(project)
                manager.openFiles
                    .filter { file -> file.isComposeKotlinFile(project) }
                    .forEach { file ->
                        EditorNotifications.getInstance(project).updateNotifications(file)
                        val hasComposeBlocksEditor = manager.getEditors(file).any { editor ->
                            editor is ComposeBlocksUnifiedFileEditor
                        }
                        if (!enabled && hasComposeBlocksEditor) {
                            file.putUserData(FORCE_OPEN_KEY, null)
                            manager.closeFile(file)
                            manager.openFile(file, true)
                        }
                    }
            }
        }
    }
}

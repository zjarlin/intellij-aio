package site.addzero.composeblocks.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

abstract class ComposeBlocksFileEditorBase(
    protected val project: Project,
    file: VirtualFile,
) : UserDataHolderBase(), FileEditor, Disposable {

    protected val sourceFile: VirtualFile = file

    protected val document = requireNotNull(FileDocumentManager.getInstance().getDocument(sourceFile)) {
        "Compose Blocks requires a document-backed file"
    }

    protected val rootPanel = JPanel()

    final override fun getComponent(): JComponent = rootPanel

    final override fun getName(): String = "Compose Blocks"

    final override fun getFile(): VirtualFile = sourceFile

    override fun setState(state: FileEditorState) {
    }

    final override fun isModified(): Boolean = FileDocumentManager.getInstance().isFileModified(sourceFile)

    final override fun isValid(): Boolean = sourceFile.isValid

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
    }

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
    }

    override fun getCurrentLocation(): FileEditorLocation? = null
}

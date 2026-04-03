package site.addzero.composebuddy.designer.ui

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.psi.KtFile
import site.addzero.composebuddy.designer.model.ComposeGeneratedCode

data class ComposeWritebackResult(
    val path: String,
    val createdSiblingFile: Boolean,
)

object ComposeDesignerWritebackSupport {
    fun currentContextKotlinFile(project: Project): VirtualFile? {
        val file = currentContextFile(project) ?: return null
        return file.takeIf { it.extension == "kt" }
    }

    fun findSiblingKotlinFile(
        project: Project,
        functionName: String,
    ): VirtualFile? {
        currentContextKotlinFile(project)?.let { return it }
        val siblingDir = currentContextFile(project)?.parent ?: project.guessProjectDir() ?: return null
        val fileName = "$functionName.kt"
        return siblingDir.findChild(fileName)
    }

    fun ensureSiblingKotlinFile(
        project: Project,
        functionName: String,
    ): VirtualFile? {
        currentContextKotlinFile(project)?.let { return it }
        val siblingDir = currentContextFile(project)?.parent ?: project.guessProjectDir() ?: return null
        val fileName = "$functionName.kt"
        siblingDir.findChild(fileName)?.let { return it }
        return WriteAction.compute<VirtualFile?, Throwable> {
            siblingDir.findChild(fileName) ?: siblingDir.createChildData(this, fileName)
        }
    }

    fun packageNameForTarget(
        project: Project,
        targetFile: VirtualFile,
    ): String {
        val existingPackage = (PsiManager.getInstance(project).findFile(targetFile) as? KtFile)
            ?.packageFqName
            ?.asString()
            .orEmpty()
        if (existingPackage.isNotBlank()) {
            return existingPackage
        }
        val packageName = currentContextFile(project)
            ?.let { PsiManager.getInstance(project).findFile(it) as? KtFile }
            ?.packageFqName
            ?.asString()
            .orEmpty()
        return packageName
    }

    fun writeToSpecificFile(
        project: Project,
        targetFile: VirtualFile,
        packageName: String,
        generatedCode: ComposeGeneratedCode,
    ): ComposeWritebackResult {
        WriteCommandAction.runWriteCommandAction(project) {
            VfsUtil.saveText(targetFile, renderFileContent(packageName, generatedCode))
        }
        return ComposeWritebackResult(targetFile.path, createdSiblingFile = true)
    }

    fun documentFor(file: VirtualFile): Document? {
        return FileDocumentManager.getInstance().getDocument(file)
    }

    fun openInEditor(
        project: Project,
        file: VirtualFile,
    ) {
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    private fun currentContextFile(project: Project): VirtualFile? {
        val editorManager = FileEditorManager.getInstance(project)
        val selectedEditor = editorManager.selectedTextEditor
        return selectedEditor?.let { FileDocumentManager.getInstance().getFile(it.document) }
            ?: editorManager.selectedFiles.firstOrNull()
    }

    private fun renderFileContent(
        packageName: String,
        generatedCode: ComposeGeneratedCode,
    ): String {
        return buildString {
            if (packageName.isNotBlank()) {
                appendLine("package $packageName")
                appendLine()
            }
            generatedCode.imports.forEach { appendLine("import $it") }
            appendLine()
            append(generatedCode.functionText)
        }.trimEnd() + "\n"
    }
}

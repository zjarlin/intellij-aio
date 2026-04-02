package site.addzero.composebuddy.designer.ui

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import site.addzero.composebuddy.designer.model.ComposeGeneratedCode

data class ComposeWritebackResult(
    val path: String,
    val replacedExistingFunction: Boolean,
)

object ComposeDesignerWritebackSupport {
    fun writeToKotlinFile(
        project: Project,
        functionName: String,
        generatedCode: ComposeGeneratedCode,
    ): ComposeWritebackResult? {
        val editorManager = FileEditorManager.getInstance(project)
        val selectedEditor = editorManager.selectedTextEditor
        val selectedFile = selectedEditor?.let { FileDocumentManager.getInstance().getFile(it.document) }
        val psiFile = selectedFile?.let { PsiManager.getInstance(project).findFile(it) as? KtFile }

        return if (psiFile != null && selectedFile != null) {
            updateExistingKotlinFile(project, psiFile, functionName, generatedCode)
            editorManager.openFile(selectedFile, true)
            ComposeWritebackResult(selectedFile.path, replacedExistingFunction = true)
        } else {
            createNewKotlinFile(project, functionName, generatedCode)
        }
    }

    private fun updateExistingKotlinFile(
        project: Project,
        file: KtFile,
        functionName: String,
        generatedCode: ComposeGeneratedCode,
    ) {
        val psiFactory = KtPsiFactory(project)
        WriteCommandAction.runWriteCommandAction(project) {
            val existingFunction = file.declarations.filterIsInstance<KtNamedFunction>().firstOrNull { it.name == functionName }
            val function = psiFactory.createFunction(generatedCode.functionText)
            if (existingFunction != null) {
                existingFunction.replace(function)
            } else {
                if (file.text.isNotBlank()) {
                    file.add(psiFactory.createWhiteSpace("\n\n"))
                }
                file.add(function)
            }
            ensureImports(file, generatedCode.imports)
            PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(
                PsiDocumentManager.getInstance(project).getDocument(file) ?: return@runWriteCommandAction
            )
        }
    }

    private fun createNewKotlinFile(
        project: Project,
        functionName: String,
        generatedCode: ComposeGeneratedCode,
    ): ComposeWritebackResult? {
        val baseDir = project.baseDir ?: return null
        val targetFile = baseDir.findChild("$functionName.kt") ?: baseDir.createChildData(this, "$functionName.kt")
        val content = buildString {
            generatedCode.imports.forEach { appendLine("import $it") }
            appendLine()
            append(generatedCode.functionText)
        }
        WriteCommandAction.runWriteCommandAction(project) {
            VfsUtil.saveText(targetFile, content)
        }
        FileEditorManager.getInstance(project).openFile(targetFile, true)
        return ComposeWritebackResult(targetFile.path, replacedExistingFunction = false)
    }

    private fun ensureImports(file: KtFile, imports: Set<String>) {
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return
        val existingImports = file.importDirectives.mapNotNull { it.importPath?.pathStr }.toSet()
        val missingImports = imports.filterNot { it in existingImports }
        if (missingImports.isEmpty()) return

        val insertOffset = file.importList?.textRange?.endOffset
            ?: file.packageDirective?.textRange?.endOffset
            ?: 0
        val prefix = if (insertOffset == 0) "" else "\n"
        val suffix = if (file.importList == null) "\n" else ""
        document.insertString(
            insertOffset,
            prefix + missingImports.joinToString("\n") { "import $it" } + suffix,
        )
        PsiDocumentManager.getInstance(file.project).commitDocument(document)
    }
}

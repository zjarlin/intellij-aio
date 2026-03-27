package site.addzero.gradle.catalog

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.PsiElementRenameHandler

class DynamicCatalogRenameHandler : PsiElementRenameHandler() {

    override fun isAvailableOnDataContext(dataContext: DataContext): Boolean {
        val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return false
        val file = CommonDataKeys.PSI_FILE.getData(dataContext) ?: return false
        return DynamicCatalogReferenceSupport.resolveTargetEntry(file, editor.caretModel.offset) != null
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
        val target = DynamicCatalogReferenceSupport.resolveTargetEntry(file, editor.caretModel.offset)
        if (target != null) {
            rename(target, project, findNameSuggestionContext(file, editor), editor)
            return
        }
        super.invoke(project, editor, file, dataContext)
    }

    private fun findNameSuggestionContext(file: PsiFile, editor: Editor): PsiElement {
        return file.findElementAt(editor.caretModel.offset)
            ?: file.findElementAt((editor.caretModel.offset - 1).coerceAtLeast(0))
            ?: file
    }
}

package site.addzero.gradle.catalog

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class DynamicCatalogRenamePsiElementProcessor : RenamePsiElementProcessor() {

    override fun canProcessElement(element: PsiElement): Boolean {
        return element is KtStringTemplateExpression ||
            element is KtLiteralStringTemplateEntry
    }

    override fun substituteElementToRename(element: PsiElement, editor: Editor?): PsiElement? {
        return DynamicCatalogReferenceSupport.resolveTargetEntry(element) ?: element
    }
}

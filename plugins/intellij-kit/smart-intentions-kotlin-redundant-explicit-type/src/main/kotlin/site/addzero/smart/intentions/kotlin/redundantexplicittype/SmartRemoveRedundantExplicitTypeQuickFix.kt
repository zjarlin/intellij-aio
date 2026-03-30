package site.addzero.smart.intentions.kotlin.redundantexplicittype

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtProperty
import site.addzero.smart.intentions.core.SmartIntentionsMessages

class SmartRemoveRedundantExplicitTypeQuickFix(
    property: KtProperty,
) : LocalQuickFixAndIntentionActionOnPsiElement(property) {
    override fun getText(): String {
        return SmartIntentionsMessages.REMOVE_REDUNDANT_EXPLICIT_TYPE
    }

    override fun getFamilyName(): String {
        return SmartIntentionsMessages.FAMILY_NAME
    }

    override fun startInWriteAction(): Boolean {
        return true
    }

    override fun isAvailable(
        project: Project,
        file: PsiFile,
        startElement: com.intellij.psi.PsiElement,
        endElement: com.intellij.psi.PsiElement,
    ): Boolean {
        val property = startElement as? KtProperty ?: return false
        return RedundantExplicitTypeSupport.isApplicable(property)
    }

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: com.intellij.psi.PsiElement,
        endElement: com.intellij.psi.PsiElement,
    ) {
        val property = startElement as? KtProperty ?: return
        RedundantExplicitTypeSupport.apply(property)
    }
}

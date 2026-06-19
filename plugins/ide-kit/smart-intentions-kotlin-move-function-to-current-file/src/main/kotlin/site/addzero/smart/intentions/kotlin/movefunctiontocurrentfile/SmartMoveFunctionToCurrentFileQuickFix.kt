package site.addzero.smart.intentions.kotlin.movefunctiontocurrentfile

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtCallExpression
import com.intellij.psi.PsiElement
import site.addzero.smart.intentions.core.SmartIntentionsMessages

class SmartMoveFunctionToCurrentFileQuickFix(
    element: PsiElement,
) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
    override fun getText(): String {
        return SmartIntentionsMessages.MOVE_FUNCTION_TO_CURRENT_FILE
    }

    override fun getFamilyName(): String {
        return SmartIntentionsMessages.FAMILY_NAME
    }

    override fun startInWriteAction(): Boolean {
        return false
    }

    override fun isAvailable(
        project: Project,
        file: PsiFile,
        startElement: PsiElement,
        endElement: PsiElement,
    ): Boolean {
        val call = startElement.getStrictCallExpression() ?: return false
        val currentFile = file as? org.jetbrains.kotlin.psi.KtFile ?: return false
        return MoveFunctionToCurrentFileSupport.isApplicable(currentFile, call)
    }

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement,
    ) {
        val call = startElement.getStrictCallExpression() ?: return
        val currentFile = file as? org.jetbrains.kotlin.psi.KtFile ?: return
        MoveFunctionToCurrentFileSupport.apply(currentFile, call)
    }

    private fun PsiElement.getStrictCallExpression(): KtCallExpression? {
        return generateSequence(this) { current -> current.parent }
            .filterIsInstance<KtCallExpression>()
            .firstOrNull()
    }
}

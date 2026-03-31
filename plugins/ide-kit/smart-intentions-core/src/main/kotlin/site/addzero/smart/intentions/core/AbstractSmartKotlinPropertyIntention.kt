package site.addzero.smart.intentions.core

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

abstract class AbstractSmartKotlinPropertyIntention : PsiElementBaseIntentionAction(), IntentionAction {
    final override fun getFamilyName(): String {
        return SmartIntentionsMessages.FAMILY_NAME
    }

    final override fun startInWriteAction(): Boolean {
        return false
    }

    final override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val property = element.getNonStrictParentOfType<KtProperty>() ?: return false
        val caretOffset = editor?.caretModel?.offset ?: element.textOffset
        return isApplicableTo(property, caretOffset)
    }

    final override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val property = element.getNonStrictParentOfType<KtProperty>() ?: return
        val caretOffset = editor?.caretModel?.offset ?: element.textOffset
        if (!isApplicableTo(property, caretOffset)) {
            return
        }

        SmartPsiWriteSupport.runWriteCommand(project, text) {
            invokeForProperty(project, editor, property)
        }
    }

    protected abstract fun isApplicableTo(property: KtProperty, caretOffset: Int): Boolean

    protected abstract fun invokeForProperty(project: Project, editor: Editor?, property: KtProperty)
}

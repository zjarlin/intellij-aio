package site.addzero.smart.intentions.kotlin.redundantexplicittype

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import site.addzero.smart.intentions.core.SmartIntentionsMessages

class SmartRemoveProjectRedundantExplicitTypeIntention : PsiElementBaseIntentionAction(), IntentionAction {
    override fun getFamilyName(): String {
        return SmartIntentionsMessages.FAMILY_NAME
    }

    override fun getText(): String {
        return SmartIntentionsMessages.REMOVE_PROJECT_REDUNDANT_EXPLICIT_TYPE
    }

    override fun startInWriteAction(): Boolean {
        return false
    }

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val property = element.getNonStrictParentOfType<KtProperty>() ?: return false
        val caretOffset = editor?.caretModel?.offset ?: element.textOffset
        return RedundantExplicitTypeSupport.isApplicable(property, caretOffset)
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val property = element.getNonStrictParentOfType<KtProperty>() ?: return
        val caretOffset = editor?.caretModel?.offset ?: element.textOffset
        if (!RedundantExplicitTypeSupport.isApplicable(property, caretOffset)) {
            return
        }
        ProjectRedundantExplicitTypeSupport.apply(project)
    }
}

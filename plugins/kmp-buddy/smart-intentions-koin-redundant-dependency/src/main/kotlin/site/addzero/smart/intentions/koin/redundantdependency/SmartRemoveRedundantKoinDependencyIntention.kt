package site.addzero.smart.intentions.koin.redundantdependency

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import site.addzero.smart.intentions.core.SmartIntentionsMessages

class SmartRemoveRedundantKoinDependencyIntention : PsiElementBaseIntentionAction(), IntentionAction {
    override fun getFamilyName(): String {
        return SmartIntentionsMessages.FAMILY_NAME
    }

    override fun getText(): String {
        return SmartIntentionsMessages.REMOVE_REDUNDANT_KOIN_DEPENDENCY
    }

    override fun startInWriteAction(): Boolean {
        return false
    }

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val parameter = element.getStrictParentOfType<KtParameter>() ?: return false
        return RedundantKoinDependencySupport.findCandidate(parameter) != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val parameter = element.getStrictParentOfType<KtParameter>() ?: return
        val candidate = RedundantKoinDependencySupport.findCandidate(parameter) ?: return
        candidate.apply(project)
    }
}

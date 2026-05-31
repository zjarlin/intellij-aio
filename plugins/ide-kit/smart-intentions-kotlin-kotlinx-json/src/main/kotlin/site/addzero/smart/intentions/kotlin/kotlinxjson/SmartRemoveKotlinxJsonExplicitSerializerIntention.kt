package site.addzero.smart.intentions.kotlin.kotlinxjson

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCallExpression
import site.addzero.smart.intentions.core.SmartIntentionsMessages
import site.addzero.smart.intentions.core.SmartPsiWriteSupport

class SmartRemoveKotlinxJsonExplicitSerializerIntention : PsiElementBaseIntentionAction(), IntentionAction {
    override fun getFamilyName(): String {
        return SmartIntentionsMessages.FAMILY_NAME
    }

    override fun getText(): String {
        return SmartIntentionsMessages.REMOVE_KOTLINX_JSON_EXPLICIT_SERIALIZER
    }

    override fun startInWriteAction(): Boolean {
        return false
    }

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        return findApplicableCall(element) != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val call = findApplicableCall(element) ?: return
        SmartPsiWriteSupport.runWriteCommand(project, text) {
            KotlinxJsonExplicitSerializerSupport.apply(call)
        }
    }

    private fun findApplicableCall(element: PsiElement): KtCallExpression? {
        return generateSequence(element) { current -> current.parent }
            .filterIsInstance<KtCallExpression>()
            .firstOrNull(KotlinxJsonExplicitSerializerSupport::isApplicable)
    }
}

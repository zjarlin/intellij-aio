package site.addzero.smart.intentions.kotlin.expressiontoblock

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtFile
import site.addzero.smart.intentions.core.SmartIntentionsMessages
import site.addzero.smart.intentions.core.SmartPsiWriteSupport

class SmartConvertExpressionToBlockIntention : PsiElementBaseIntentionAction(), IntentionAction {
    override fun getFamilyName(): String {
        return SmartIntentionsMessages.FAMILY_NAME
    }

    override fun getText(): String {
        return SmartIntentionsMessages.CONVERT_EXPRESSION_TO_BLOCK
    }

    override fun startInWriteAction(): Boolean {
        return false
    }

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val file = element.containingFile as? KtFile ?: return false
        return ExpressionToBlockSupport.isApplicable(file)
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val file = element.containingFile as? KtFile ?: return
        if (!ExpressionToBlockSupport.isApplicable(file)) return

        SmartPsiWriteSupport.runWriteCommand(project, text) {
            ExpressionToBlockSupport.apply(file)
        }
    }
}

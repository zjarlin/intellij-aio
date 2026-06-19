package site.addzero.smart.intentions.kotlin.movefunctiontocurrentfile

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import site.addzero.smart.intentions.core.SmartInspectionSupport
import site.addzero.smart.intentions.core.SmartIntentionsMessages

class SmartMoveFunctionToCurrentFileInspection : LocalInspectionTool() {
    override fun getShortName(): String {
        return SmartIntentionsMessages.MOVE_FUNCTION_TO_CURRENT_FILE_SHORT_NAME
    }

    override fun getDisplayName(): String {
        return SmartIntentionsMessages.MOVE_FUNCTION_TO_CURRENT_FILE_DISPLAY_NAME
    }

    override fun getGroupDisplayName(): String {
        return SmartIntentionsMessages.FAMILY_NAME
    }

    override fun getStaticDescription(): String {
        return SmartIntentionsMessages.MOVE_FUNCTION_TO_CURRENT_FILE_DESCRIPTION
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)

                val callee = expression.calleeExpression as? KtNameReferenceExpression ?: return
                val currentFile = expression.containingFile as? KtFile ?: return
                if (!MoveFunctionToCurrentFileSupport.isApplicable(currentFile, expression)) {
                    return
                }

                SmartInspectionSupport.registerProblem(
                    holder,
                    callee,
                    SmartIntentionsMessages.MOVE_FUNCTION_TO_CURRENT_FILE_DESCRIPTION,
                    SmartMoveFunctionToCurrentFileQuickFix(callee),
                )
            }
        }
    }
}

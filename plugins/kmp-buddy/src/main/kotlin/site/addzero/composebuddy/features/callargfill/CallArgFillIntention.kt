package site.addzero.composebuddy.features.callargfill

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import site.addzero.composebuddy.ComposeBuddyBundle

class CallArgFillIntention : PsiElementBaseIntentionAction() {
    override fun getFamilyName(): String = ComposeBuddyBundle.message("plugin.name")

    override fun getText(): String = ComposeBuddyBundle.message("intention.fill.call.arguments.from.parameters")

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val call = element.getStrictParentOfType<KtCallExpression>() ?: return false
        return CallArgFillAnalysis.analyze(call) != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val call = element.getStrictParentOfType<KtCallExpression>() ?: return
        val analysis = CallArgFillAnalysis.analyze(call) ?: return
        CallArgFillRefactor(project).apply(analysis)
    }
}

package site.addzero.composebuddy.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.analysis.ComposeFunctionSupport
import site.addzero.composebuddy.refactor.ComposeRefactorEngine

class FlattenUsedObjectParametersIntention : PsiElementBaseIntentionAction() {
    override fun getFamilyName(): String = ComposeBuddyBundle.message("plugin.name")

    override fun getText(): String = ComposeBuddyBundle.message("intention.flatten.used.object.params")

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val function = element.getStrictParentOfType<KtNamedFunction>() ?: return false
        val analysis = ComposeFunctionSupport.analyzeFlattenableObjectParameters(function) ?: return false
        return analysis.candidates.isNotEmpty()
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val function = element.getStrictParentOfType<KtNamedFunction>() ?: return
        val analysis = ComposeFunctionSupport.analyzeFlattenableObjectParameters(function) ?: return
        ComposeRefactorEngine(project).flattenUsedObjectParameters(analysis)
    }
}

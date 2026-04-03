package site.addzero.composebuddy.features.uivariant

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import site.addzero.composebuddy.ComposeBuddyBundle

class UiVariantIntention : PsiElementBaseIntentionAction() {
    override fun getFamilyName(): String = ComposeBuddyBundle.message("plugin.name")

    override fun getText(): String = ComposeBuddyBundle.message("intention.convert.boolean.to.variant")

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val function = element.getStrictParentOfType<KtNamedFunction>() ?: return false
        return UiVariantAnalysis.analyze(function) != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val function = element.getStrictParentOfType<KtNamedFunction>() ?: return
        val analysis = UiVariantAnalysis.analyze(function) ?: return
        UiVariantRefactor(project).apply(analysis)
    }
}

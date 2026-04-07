package site.addzero.composebuddy.features.containerwrap

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import site.addzero.composebuddy.ComposeBuddyBundle

class ContainerWrapIntention : PsiElementBaseIntentionAction(), DumbAware {
    override fun getFamilyName(): String = ComposeBuddyBundle.message("plugin.name")

    override fun getText(): String = ComposeBuddyBundle.message("intention.wrap.higher.order.container")

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val currentEditor = editor ?: return false
        val function = element.getStrictParentOfType<KtNamedFunction>() ?: return false
        return ContainerWrapAnalysis.analyze(function, currentEditor) != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val currentEditor = editor ?: return
        val function = element.getStrictParentOfType<KtNamedFunction>() ?: return
        val analysis = ContainerWrapAnalysis.analyze(function, currentEditor) ?: return
        ContainerWrapRefactor(project).apply(analysis)
    }
}

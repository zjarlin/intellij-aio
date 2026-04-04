package site.addzero.composebuddy.features.slotextract

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import site.addzero.composebuddy.ComposeBuddyBundle

class SlotExtractIntention : PsiElementBaseIntentionAction() {
    override fun getFamilyName(): String = ComposeBuddyBundle.message("plugin.name")

    override fun getText(): String = ComposeBuddyBundle.message("intention.extract.slot.parameter")

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val currentEditor = editor ?: return false
        val function = element.getStrictParentOfType<KtNamedFunction>() ?: return false
        return SlotExtractAnalysis.analyze(function, currentEditor) != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val currentEditor = editor ?: return
        val function = element.getStrictParentOfType<KtNamedFunction>() ?: return
        val analysis = SlotExtractAnalysis.analyze(function, currentEditor) ?: return
        SlotExtractRefactor(project).apply(analysis)
    }
}

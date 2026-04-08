package site.addzero.composebuddy.features.slotspi

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import site.addzero.composebuddy.ComposeBuddyBundle

class SlotSpiIntention : PsiElementBaseIntentionAction(), DumbAware {
    override fun getFamilyName(): String = ComposeBuddyBundle.message("plugin.name")

    override fun getText(): String = ComposeBuddyBundle.message("intention.extract.slot.spi")

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        return SlotSpiAnalysis.analyze(element) != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val analysis = SlotSpiAnalysis.analyze(element) ?: return
        SlotSpiRefactor(project).apply(analysis)
    }
}

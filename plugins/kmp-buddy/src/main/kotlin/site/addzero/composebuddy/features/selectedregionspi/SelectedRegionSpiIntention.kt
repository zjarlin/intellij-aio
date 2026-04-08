package site.addzero.composebuddy.features.selectedregionspi

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import site.addzero.composebuddy.ComposeBuddyBundle

class SelectedRegionSpiIntention : PsiElementBaseIntentionAction(), DumbAware {
    override fun getFamilyName(): String = ComposeBuddyBundle.message("plugin.name")

    override fun getText(): String = ComposeBuddyBundle.message("intention.extract.selected.region.spi")

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        return SelectedRegionSpiAnalysis.analyze(element, editor) != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val analysis = SelectedRegionSpiAnalysis.analyze(element, editor) ?: return
        SelectedRegionSpiRefactor(project).apply(analysis)
    }
}

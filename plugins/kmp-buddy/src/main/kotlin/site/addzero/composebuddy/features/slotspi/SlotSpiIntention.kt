package site.addzero.composebuddy.features.slotspi

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.application.ApplicationManager
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
        return SlotSpiAnalysis.analyze(element, editor) != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val analysis = SlotSpiAnalysis.analyze(element, editor) ?: return
        val selectedTargets = selectTargets(project, editor, analysis) ?: return
        SlotSpiRefactor(project).apply(analysis.copy(targets = selectedTargets))
    }

    private fun selectTargets(
        project: Project,
        editor: Editor?,
        analysis: SlotSpiAnalysisResult,
    ): List<SlotSpiTarget>? {
        if (analysis.targets.size <= 1) {
            return analysis.targets
        }
        if (editor?.selectionModel?.hasSelection() == true) {
            return analysis.targets
        }
        if (ApplicationManager.getApplication().isUnitTestMode) {
            return analysis.targets
        }
        val dialog = SlotSpiTargetSelectionDialog(project, analysis.targets)
        if (!dialog.showAndGet()) {
            return null
        }
        return dialog.selectedTargets()
    }
}

package site.addzero.composebuddy.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.analysis.ComposeFunctionSupport
import site.addzero.composebuddy.refactor.ComposeRefactorEngine
import site.addzero.composebuddy.ui.ComposeSignatureNormalizationDialog

class NormalizeComposeSignatureAction : AnAction(), DumbAware {
    override fun update(event: AnActionEvent) {
        val project = event.project
        val editor = event.getData(CommonDataKeys.EDITOR)
        val psiFile = event.getData(CommonDataKeys.PSI_FILE)
        val function = psiFile?.findElementAt(editor?.caretModel?.offset ?: -1)?.getStrictParentOfType<KtNamedFunction>()
        val analysis = function?.let { ComposeFunctionSupport.analyzeSignature(it) }
        event.presentation.isEnabledAndVisible = project != null && analysis != null &&
            (analysis.propsCandidates.isNotEmpty() || analysis.eventCandidates.isNotEmpty() || analysis.statePairs.isNotEmpty())
        event.presentation.text = ComposeBuddyBundle.message("intention.normalize.signature")
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = event.getData(CommonDataKeys.PSI_FILE) ?: return
        val function = psiFile.findElementAt(editor.caretModel.offset)?.getStrictParentOfType<KtNamedFunction>() ?: return
        val analysis = ComposeFunctionSupport.analyzeSignature(function) ?: return
        val dialog = ComposeSignatureNormalizationDialog(project, analysis)
        if (!dialog.showAndGet()) return
        ComposeRefactorEngine(project).normalizeSignature(analysis, dialog.buildRequest())
    }
}

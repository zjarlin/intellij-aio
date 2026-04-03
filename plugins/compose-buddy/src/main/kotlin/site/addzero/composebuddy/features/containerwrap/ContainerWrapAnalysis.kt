package site.addzero.composebuddy.features.containerwrap

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import site.addzero.composebuddy.support.ComposePsiSupport

data class ContainerWrapAnalysisResult(
    val function: KtNamedFunction,
    val targetCall: KtCallExpression,
)

object ContainerWrapAnalysis {
    fun analyze(function: KtNamedFunction, editor: Editor): ContainerWrapAnalysisResult? {
        if (!ComposePsiSupport.isComposable(function)) return null
        if (!editor.selectionModel.hasSelection()) return null
        val selectionStart = editor.selectionModel.selectionStart
        val selectionEnd = editor.selectionModel.selectionEnd
        val caretOffset = editor.caretModel.offset
        val candidates = function.collectDescendantsOfType<KtCallExpression>()
            .filter { call -> call.calleeExpression?.text in ComposePsiSupport.layoutContainerNames() }
            .filter { call -> call.lambdaArguments.isNotEmpty() }
            .filter { call -> ComposePsiSupport.selectedRangeContains(selectionStart, selectionEnd, call) }
        if (candidates.isEmpty()) return null
        val best = candidates.filter { it.textRange.contains(caretOffset) }.ifEmpty { candidates }
            .maxByOrNull { it.textRange.length }
            ?: return null
        return ContainerWrapAnalysisResult(function, best)
    }
}

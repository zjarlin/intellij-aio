package site.addzero.composebuddy.features.slotextract

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import site.addzero.composebuddy.support.ComposePsiSupport

data class SlotExtractAnalysisResult(
    val function: KtNamedFunction,
    val call: KtCallExpression,
)

object SlotExtractAnalysis {
    fun analyze(function: KtNamedFunction, editor: Editor): SlotExtractAnalysisResult? {
        if (!ComposePsiSupport.isComposable(function)) return null
        if (!editor.selectionModel.hasSelection()) return null
        val selectionStart = editor.selectionModel.selectionStart
        val selectionEnd = editor.selectionModel.selectionEnd
        val call = function.collectDescendantsOfType<KtCallExpression>()
            .firstOrNull { candidate ->
                candidate.lambdaArguments.isNotEmpty() &&
                    ComposePsiSupport.selectedRangeContains(selectionStart, selectionEnd, candidate)
            } ?: return null
        return SlotExtractAnalysisResult(function, call)
    }
}

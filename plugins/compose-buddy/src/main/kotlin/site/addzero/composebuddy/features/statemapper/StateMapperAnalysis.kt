package site.addzero.composebuddy.features.statemapper

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import site.addzero.composebuddy.support.ComposePsiSupport

data class StateMapperAnalysisResult(
    val function: KtNamedFunction,
    val expression: KtExpression,
)

object StateMapperAnalysis {
    fun analyze(function: KtNamedFunction, editor: Editor): StateMapperAnalysisResult? {
        if (!ComposePsiSupport.isComposable(function)) return null
        if (!editor.selectionModel.hasSelection()) return null
        val selectionStart = editor.selectionModel.selectionStart
        val selectionEnd = editor.selectionModel.selectionEnd
        val ifExpression = function.collectDescendantsOfType<KtIfExpression>()
            .firstOrNull { ComposePsiSupport.selectedRangeContains(selectionStart, selectionEnd, it) }
        if (ifExpression != null) return StateMapperAnalysisResult(function, ifExpression)
        val whenExpression = function.collectDescendantsOfType<KtWhenExpression>()
            .firstOrNull { ComposePsiSupport.selectedRangeContains(selectionStart, selectionEnd, it) }
            ?: return null
        return StateMapperAnalysisResult(function, whenExpression)
    }
}

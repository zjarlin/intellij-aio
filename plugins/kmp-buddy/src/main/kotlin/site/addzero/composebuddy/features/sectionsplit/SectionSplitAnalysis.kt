package site.addzero.composebuddy.features.sectionsplit

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import site.addzero.composebuddy.support.ComposePsiSupport

data class SectionSplitAnalysisResult(
    val function: KtNamedFunction,
    val sectionCall: KtCallExpression,
)

object SectionSplitAnalysis {
    fun analyze(function: KtNamedFunction, editor: Editor): SectionSplitAnalysisResult? {
        if (!editor.selectionModel.hasSelection()) return null
        val selectionStart = editor.selectionModel.selectionStart
        val selectionEnd = editor.selectionModel.selectionEnd
        val target = ComposePsiSupport.findTopLevelLayoutChildren(function)
            .firstOrNull { ComposePsiSupport.selectedRangeContains(selectionStart, selectionEnd, it) }
            ?: return null
        return SectionSplitAnalysisResult(function, target)
    }
}

package site.addzero.composebuddy.features.previewsample

import org.jetbrains.kotlin.psi.KtNamedFunction
import site.addzero.composebuddy.support.ComposePsiSupport

data class PreviewSampleAnalysisResult(
    val function: KtNamedFunction,
)

object PreviewSampleAnalysis {
    fun analyze(function: KtNamedFunction): PreviewSampleAnalysisResult? {
        if (!ComposePsiSupport.isComposable(function)) return null
        if (function.name.isNullOrBlank()) return null
        return PreviewSampleAnalysisResult(function)
    }
}

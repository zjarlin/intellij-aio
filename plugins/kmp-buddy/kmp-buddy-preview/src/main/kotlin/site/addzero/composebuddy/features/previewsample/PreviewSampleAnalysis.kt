package site.addzero.composebuddy.features.previewsample

import org.jetbrains.kotlin.psi.KtNamedFunction
import site.addzero.composebuddy.preview.support.PreviewComposePsiSupport

data class PreviewSampleAnalysisResult(
    val function: KtNamedFunction,
)

object PreviewSampleAnalysis {
    fun analyze(function: KtNamedFunction): PreviewSampleAnalysisResult? {
        if (!PreviewComposePsiSupport.isComposable(function)) return null
        if (function.name.isNullOrBlank()) return null
        return PreviewSampleAnalysisResult(function)
    }
}

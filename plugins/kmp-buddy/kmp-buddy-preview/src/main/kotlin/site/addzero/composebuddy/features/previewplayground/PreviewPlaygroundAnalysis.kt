package site.addzero.composebuddy.features.previewplayground

import org.jetbrains.kotlin.psi.KtNamedFunction
import site.addzero.composebuddy.preview.support.PreviewComposePsiSupport
import site.addzero.composebuddy.support.ComposePreviewSupport

data class PreviewPlaygroundAnalysisResult(
    val function: KtNamedFunction,
)

object PreviewPlaygroundAnalysis {
    fun analyze(function: KtNamedFunction): PreviewPlaygroundAnalysisResult? {
        if (!PreviewComposePsiSupport.isComposable(function)) {
            return null
        }
        if (function.name.isNullOrBlank()) {
            return null
        }
        if (!ComposePreviewSupport.canRenderQuickPreview(function)) {
            return null
        }
        return PreviewPlaygroundAnalysisResult(function)
    }
}

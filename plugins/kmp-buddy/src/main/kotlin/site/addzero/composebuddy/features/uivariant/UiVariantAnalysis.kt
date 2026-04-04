package site.addzero.composebuddy.features.uivariant

import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import site.addzero.composebuddy.support.ComposePsiSupport

data class UiVariantAnalysisResult(
    val function: KtNamedFunction,
    val booleanParameter: KtParameter,
)

object UiVariantAnalysis {
    fun analyze(function: KtNamedFunction): UiVariantAnalysisResult? {
        if (!ComposePsiSupport.isComposable(function)) return null
        val parameter = function.valueParameters.firstOrNull { it.typeReference?.text == "Boolean" } ?: return null
        val bodyText = function.bodyExpression?.text ?: return null
        if (!bodyText.contains(parameter.name ?: "")) return null
        if (!bodyText.contains("if")) return null
        return UiVariantAnalysisResult(function, parameter)
    }
}

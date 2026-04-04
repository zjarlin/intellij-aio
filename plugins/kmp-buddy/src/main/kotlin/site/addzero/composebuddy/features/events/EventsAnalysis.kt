package site.addzero.composebuddy.features.events

import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import site.addzero.composebuddy.support.ComposePsiSupport

data class EventsAnalysisResult(
    val function: KtNamedFunction,
    val callbacks: List<KtParameter>,
)

object EventsAnalysis {
    fun analyze(function: KtNamedFunction): EventsAnalysisResult? {
        if (!ComposePsiSupport.isComposable(function)) return null
        val callbacks = function.valueParameters.filter { it.typeReference?.text?.contains("->") == true }
        if (callbacks.size < 2) return null
        return EventsAnalysisResult(function, callbacks)
    }
}

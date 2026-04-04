package site.addzero.composebuddy.features.stability

import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import site.addzero.composebuddy.support.ComposePsiSupport

data class StabilityIssue(
    val parameter: KtParameter,
    val replacementType: String,
)

object StabilityAnalysis {
    fun analyze(function: KtNamedFunction): List<StabilityIssue> {
        if (!ComposePsiSupport.isComposable(function)) return emptyList()
        return function.valueParameters.mapNotNull { parameter ->
            val typeText = parameter.typeReference?.text ?: return@mapNotNull null
            val replacement = when {
                typeText.startsWith("MutableList<") || typeText.startsWith("ArrayList<") -> typeText.replaceBefore("<", "List")
                typeText.startsWith("MutableSet<") -> typeText.replaceBefore("<", "Set")
                typeText.startsWith("HashMap<") || typeText.startsWith("MutableMap<") -> typeText.replaceBefore("<", "Map")
                else -> null
            } ?: return@mapNotNull null
            StabilityIssue(parameter, replacement)
        }
    }
}

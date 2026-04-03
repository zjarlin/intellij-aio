package site.addzero.composebuddy.features.statehoist

import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import site.addzero.composebuddy.support.ComposePsiSupport

data class StateHoistIssue(
    val function: KtNamedFunction,
    val property: KtProperty,
    val stateName: String,
    val initialExpression: String,
)

object StateHoistAnalysis {
    fun analyze(function: KtNamedFunction): StateHoistIssue? {
        if (!ComposePsiSupport.isComposable(function)) return null
        val property = function.collectDescendantsOfType<KtProperty>().firstOrNull { candidate ->
            val initializer = candidate.initializer?.text ?: return@firstOrNull false
            initializer.startsWith("remember") && initializer.contains("mutableStateOf(")
        } ?: return null
        val propertyName = property.name ?: return null
        val initialExpression = Regex("mutableStateOf\\((.*)\\)").find(property.initializer?.text.orEmpty())
            ?.groupValues?.getOrNull(1)
            ?.trim()
            ?: return null
        return StateHoistIssue(function, property, propertyName.removeSuffix("State"), initialExpression)
    }
}

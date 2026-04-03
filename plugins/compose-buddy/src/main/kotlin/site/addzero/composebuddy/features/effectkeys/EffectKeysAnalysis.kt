package site.addzero.composebuddy.features.effectkeys

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import site.addzero.composebuddy.support.ComposePsiSupport

data class EffectKeysIssue(
    val call: KtCallExpression,
    val suggestedKeys: List<String>,
)

object EffectKeysAnalysis {
    private val targetCalls = setOf("remember", "LaunchedEffect", "DisposableEffect")

    fun analyze(function: KtNamedFunction): List<EffectKeysIssue> {
        if (!ComposePsiSupport.isComposable(function)) return emptyList()
        val parameterNames = function.valueParameters.mapNotNull { it.name }
        return function.collectDescendantsOfType<KtCallExpression>().mapNotNull { call ->
            val name = call.calleeExpression?.text ?: return@mapNotNull null
            if (name !in targetCalls) return@mapNotNull null
            val lambdaText = call.lambdaArguments.firstOrNull()?.text ?: return@mapNotNull null
            val captured = parameterNames.filter { parameterName -> Regex("\\b$parameterName\\b").containsMatchIn(lambdaText) }
            if (captured.isEmpty()) return@mapNotNull null
            if (call.valueArguments.isEmpty() || call.valueArguments.map { it.text } != captured) {
                EffectKeysIssue(call, captured)
            } else {
                null
            }
        }
    }
}

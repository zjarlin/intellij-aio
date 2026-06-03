package site.addzero.composebuddy.features.effectkeys

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import site.addzero.composebuddy.support.ComposePsiSupport

data class EffectKeysIssue(
    val call: KtCallExpression,
    val suggestedKeys: List<String>,
)

object EffectKeysAnalysis {
    private val effectCalls = setOf("LaunchedEffect", "DisposableEffect")

    fun analyze(function: KtNamedFunction): List<EffectKeysIssue> {
        if (!ComposePsiSupport.isComposable(function)) return emptyList()
        val parameterNames = function.valueParameters.mapNotNull { it.name }
        return function.collectDescendantsOfType<KtCallExpression>().mapNotNull { call ->
            val name = call.calleeExpression?.text ?: return@mapNotNull null
            if (name !in effectCalls) return@mapNotNull null
            call.lambdaArguments.firstOrNull() ?: return@mapNotNull null
            val captured = capturedParameterNames(call, parameterNames)
            if (captured.isEmpty()) return@mapNotNull null
            if (call.valueArguments.isEmpty() || call.valueArguments.map { it.text } != captured) {
                EffectKeysIssue(call, captured)
            } else {
                null
            }
        }
    }

    private fun capturedParameterNames(call: KtCallExpression, parameterNames: List<String>): List<String> {
        val referencedNames = call.lambdaArguments
            .flatMap { lambda ->
                lambda.collectDescendantsOfType<KtNameReferenceExpression>()
                    .mapNotNull { reference -> reference.getReferencedName() }
            }
            .toSet()
        return parameterNames.filter { parameterName -> parameterName in referencedNames }
    }
}

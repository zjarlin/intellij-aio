package site.addzero.composebuddy.features.parametersort

import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import site.addzero.composebuddy.support.ComposeCallSupport
import site.addzero.composebuddy.support.ComposePsiSupport

data class ParameterSortResult(
    val function: KtNamedFunction,
    val sortedParameters: List<KtParameter>,
)

object ParameterSortAnalysis {
    fun analyze(function: KtNamedFunction): ParameterSortResult? {
        if (!ComposePsiSupport.isComposable(function)) return null
        val parameters = function.valueParameters
        if (parameters.size < 2) return null
        if (hasUnsafeCallSites(function)) return null

        val sorted = sortParameters(parameters)
        if (sorted == parameters) return null
        return ParameterSortResult(function, sorted)
    }

    private fun sortParameters(parameters: List<KtParameter>): List<KtParameter> {
        val statePairs = detectStatePairs(parameters)
        val stateParameters = statePairs.flatMap { listOf(it.valueParameter, it.callbackParameter) }.toSet()

        return parameters
            .mapIndexed { index, parameter -> IndexedParameter(index, parameter) }
            .sortedWith(
                compareBy<IndexedParameter> { groupRank(it.parameter, stateParameters) }
                    .thenBy { it.index },
            )
            .map { it.parameter }
    }

    private fun groupRank(parameter: KtParameter, stateParameters: Set<KtParameter>): Int {
        return when {
            parameter in stateParameters -> 1
            isEventParameter(parameter) -> 2
            isLambdaParameter(parameter) -> 3
            else -> 0
        }
    }

    private fun hasUnsafeCallSites(function: KtNamedFunction): Boolean {
        return ComposeCallSupport.collectCallSites(function.project, function).any { call ->
            call.valueArguments.any { it.getArgumentName() == null } || call.lambdaArguments.isNotEmpty()
        }
    }

    private fun detectStatePairs(parameters: List<KtParameter>): List<StatePair> {
        return parameters.mapNotNull { parameter ->
            val name = parameter.name ?: return@mapNotNull null
            if (isLambdaParameter(parameter)) return@mapNotNull null
            val callbackName = "on${name.replaceFirstChar { it.uppercase() }}Change"
            val callback = parameters.firstOrNull { it.name == callbackName && isLambdaParameter(it) }
                ?: return@mapNotNull null
            StatePair(parameter, callback)
        }
    }

    private fun isEventParameter(parameter: KtParameter): Boolean {
        val name = parameter.name ?: return false
        return isLambdaParameter(parameter) && name.startsWith("on") && name.length > 2 && name[2].isUpperCase()
    }

    private fun isLambdaParameter(parameter: KtParameter): Boolean {
        return parameter.typeReference?.text?.contains("->") == true
    }

    private data class IndexedParameter(
        val index: Int,
        val parameter: KtParameter,
    )

    private data class StatePair(
        val valueParameter: KtParameter,
        val callbackParameter: KtParameter,
    )
}

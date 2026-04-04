package site.addzero.composebuddy.analysis

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter

data class ComposeParameterSpec(
    val name: String,
    val typeText: String,
    val defaultValueText: String? = null,
)

data class ComposeForwardedParameter(
    val parameter: KtParameter,
    val targetName: String,
)

data class ComposeWrapperAnalysisResult(
    val function: KtNamedFunction,
    val targetCall: KtCallExpression,
    val targetName: String,
    val suggestedParameters: List<ComposeParameterSpec>,
    val extraParameters: List<KtParameter>,
    val forwardedParameters: List<ComposeForwardedParameter>,
)

data class ComposeStatePair(
    val valueParameter: KtParameter,
    val callbackParameter: KtParameter,
)

data class ComposeSignatureAnalysisResult(
    val function: KtNamedFunction,
    val propsCandidates: List<KtParameter>,
    val eventCandidates: List<KtParameter>,
    val statePairs: List<ComposeStatePair>,
) {
    val callbackCount: Int
        get() = eventCandidates.size + statePairs.size

    val totalParameterCount: Int
        get() = function.valueParameters.size
}

data class ComposeFlattenProperty(
    val propertyName: String,
    val parameterName: String,
    val typeText: String,
    val defaultValueText: String? = null,
)

data class ComposeFlattenObjectParameterCandidate(
    val parameter: KtParameter,
    val flattenedProperties: List<ComposeFlattenProperty>,
)

data class ComposeFlattenObjectParameterAnalysisResult(
    val function: KtNamedFunction,
    val candidates: List<ComposeFlattenObjectParameterCandidate>,
)

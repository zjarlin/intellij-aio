package site.addzero.composebuddy.analysis

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import site.addzero.composebuddy.settings.ComposeBuddySettingsService

object ComposeFunctionSupport {
    fun isComposable(function: KtNamedFunction): Boolean {
        return function.annotationEntries.any { entry ->
            entry.shortName?.asString() == "Composable" || entry.text.endsWith(".Composable")
        }
    }

    fun analyzeWrapper(function: KtNamedFunction): ComposeWrapperAnalysisResult? {
        if (!isComposable(function)) return null
        val targetCall = findPrimaryCall(function) ?: return null
        val targetName = targetCall.calleeExpression?.text ?: return null
        if (!ComposeComponentParameterRegistry.isSupported(targetName)) return null

        val templateSpecs = ComposeComponentParameterRegistry.parametersFor(targetName)
        val settings = ComposeBuddySettingsService.getInstance().state
        val existingParameters = function.valueParameters
        val existingNames = existingParameters.mapNotNull { it.name }.toSet()
        val suggestedParameters = templateSpecs.filter { spec ->
            spec.name !in existingNames && (!isSlotParameter(spec) || settings.addTemplateParameters)
        }

        val forwardedParameters = targetCall.valueArguments.mapNotNull { argument ->
            val targetArgumentName = argument.getArgumentName()?.asName?.identifier ?: return@mapNotNull null
            val expression = argument.getArgumentExpression() as? KtNameReferenceExpression ?: return@mapNotNull null
            val parameter = existingParameters.firstOrNull { it.name == expression.getReferencedName() } ?: return@mapNotNull null
            ComposeForwardedParameter(parameter = parameter, targetName = targetArgumentName)
        }

        val forwardedParameterSet = forwardedParameters.map { it.parameter }.toSet()
        val extraParameters = existingParameters.filterNot { it in forwardedParameterSet }

        if (suggestedParameters.isEmpty() && forwardedParameters.isEmpty()) return null

        return ComposeWrapperAnalysisResult(
            function = function,
            targetCall = targetCall,
            targetName = targetName,
            suggestedParameters = suggestedParameters,
            extraParameters = extraParameters,
            forwardedParameters = forwardedParameters,
        )
    }

    fun analyzeSignature(function: KtNamedFunction): ComposeSignatureAnalysisResult? {
        if (!isComposable(function)) return null
        val parameters = function.valueParameters
        if (parameters.isEmpty()) return null

        val statePairs = detectStatePairs(parameters)
        val stateParams = statePairs.flatMap { listOf(it.valueParameter, it.callbackParameter) }.toSet()
        val eventCandidates = parameters.filter { isCallbackParameter(it) && it !in stateParams }
        val propsCandidates = parameters.filter { !isCallbackParameter(it) && it !in stateParams }

        return ComposeSignatureAnalysisResult(
            function = function,
            propsCandidates = propsCandidates,
            eventCandidates = eventCandidates,
            statePairs = statePairs,
        )
    }

    fun hasNormalizationOpportunity(project: Project, analysis: ComposeSignatureAnalysisResult): Boolean {
        val settings = ComposeBuddySettingsService.getInstance().state
        return analysis.totalParameterCount >= settings.parameterThreshold ||
            analysis.eventCandidates.size >= settings.callbackThreshold ||
            analysis.statePairs.size >= settings.statePairThreshold
    }

    private fun detectStatePairs(parameters: List<KtParameter>): List<ComposeStatePair> {
        return parameters.mapNotNull { parameter ->
            val name = parameter.name ?: return@mapNotNull null
            if (isCallbackParameter(parameter)) return@mapNotNull null
            val callbackName = "on${name.replaceFirstChar { it.uppercase() }}Change"
            val callback = parameters.firstOrNull { it.name == callbackName && isCallbackParameter(it) } ?: return@mapNotNull null
            ComposeStatePair(parameter, callback)
        }
    }

    private fun findPrimaryCall(function: KtNamedFunction): KtCallExpression? {
        function.bodyExpression?.let { body ->
            when (body) {
                is KtCallExpression -> return body
                is KtDotQualifiedExpression -> return body.selectorExpression as? KtCallExpression
                is KtBlockExpression -> {
                    val statements = body.statements
                    if (statements.size != 1) return null
                    val single = statements.single()
                    if (single is KtCallExpression) return single
                    if (single is KtDotQualifiedExpression) return single.selectorExpression as? KtCallExpression
                    return single.collectDescendantsOfType<KtCallExpression>()
                        .firstOrNull { it.getStrictParentOfType<KtNamedFunction>() == function }
                }
                else -> return body.collectDescendantsOfType<KtCallExpression>()
                    .firstOrNull { it.getStrictParentOfType<KtNamedFunction>() == function }
            }
        }
        return null
    }

    private fun isCallbackParameter(parameter: KtParameter): Boolean {
        return parameter.typeReference?.text?.contains("->") == true
    }

    private fun isSlotParameter(spec: ComposeParameterSpec): Boolean {
        return spec.typeText.contains("@Composable")
    }
}

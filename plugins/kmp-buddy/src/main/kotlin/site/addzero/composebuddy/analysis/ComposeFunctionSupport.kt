package site.addzero.composebuddy.analysis

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.idea.references.mainReference
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

    fun analyzeFlattenableObjectParameters(function: KtNamedFunction): ComposeFlattenObjectParameterAnalysisResult? {
        if (!isComposable(function)) return null
        val body = function.bodyExpression ?: return null
        val candidates = function.valueParameters.mapNotNull { parameter ->
            if (isCallbackParameter(parameter)) return@mapNotNull null
            val parameterName = parameter.name ?: return@mapNotNull null
            val directReferences = collectDirectParameterReferences(body, parameter)
            if (directReferences.isNotEmpty()) return@mapNotNull null

            val usedPropertyNames = collectUsedPropertyNames(body, parameter)
            if (usedPropertyNames.isEmpty()) return@mapNotNull null

            val propertyDefinitions = resolveReadableProperties(parameter)
            if (propertyDefinitions.isEmpty()) return@mapNotNull null

            val flattenedProperties = usedPropertyNames.mapNotNull { propertyName ->
                val definition = propertyDefinitions[propertyName] ?: return@mapNotNull null
                ComposeFlattenProperty(
                    propertyName = propertyName,
                    parameterName = buildFlattenedParameterName(parameterName, propertyName),
                    typeText = definition.typeText,
                    defaultValueText = definition.defaultValueText,
                )
            }
            if (flattenedProperties.isEmpty()) return@mapNotNull null
            ComposeFlattenObjectParameterCandidate(
                parameter = parameter,
                flattenedProperties = flattenedProperties,
            )
        }
        if (candidates.isEmpty()) return null
        return ComposeFlattenObjectParameterAnalysisResult(function, candidates)
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

    private fun collectDirectParameterReferences(body: KtExpression, parameter: KtParameter): Set<KtNameReferenceExpression> {
        return ReferencesSearch.search(parameter, LocalSearchScope(body))
            .findAll()
            .mapNotNull { it.element as? KtNameReferenceExpression }
            .filter { reference ->
                val parentDot = reference.parent as? KtDotQualifiedExpression
                parentDot?.receiverExpression != reference
            }
            .toSet()
    }

    private fun collectUsedPropertyNames(body: KtExpression, parameter: KtParameter): List<String> {
        val ordered = linkedSetOf<String>()
        ReferencesSearch.search(parameter, LocalSearchScope(body))
            .findAll()
            .mapNotNull { it.element as? KtNameReferenceExpression }
            .forEach { receiver ->
                val dot = receiver.parent as? KtDotQualifiedExpression ?: return@forEach
                if (dot.receiverExpression != receiver) return@forEach
            val selector = dot.selectorExpression as? KtNameReferenceExpression ?: return@forEach
            ordered += selector.getReferencedName()
            }
        return ordered.toList()
    }

    private fun resolveReadableProperties(parameter: KtParameter): Map<String, ResolvedPropertyDefinition> {
        val kotlinClass = resolveKtClass(parameter)
        if (kotlinClass != null) {
            return resolveFromKtClass(kotlinClass)
        }
        val psiClass = resolvePsiClass(parameter)
        if (psiClass != null) {
            return resolveFromPsiClass(psiClass)
        }
        return emptyMap()
    }

    private fun resolveKtClass(parameter: KtParameter): KtClass? {
        val typeReference = parameter.typeReference ?: return null
        val typeElement = typeReference.typeElement as? KtUserType ?: return null
        val resolved = typeElement.referenceExpression?.mainReference?.resolve()
        if (resolved is KtClass) {
            return resolved
        }
        val typeShortName = typeElement.referencedName ?: return null
        val file = parameter.containingKtFile
        return file.declarations.filterIsInstance<KtClass>().firstOrNull { it.name == typeShortName }
    }

    private fun resolvePsiClass(parameter: KtParameter): PsiClass? {
        val typeText = parameter.typeReference?.text ?: return null
        val shortName = typeText.substringBefore("<").substringAfterLast(".")
        if (shortName.isBlank()) return null
        val project = parameter.project
        return PsiShortNamesCache.getInstance(project)
            .getClassesByName(shortName, GlobalSearchScope.projectScope(project))
            .firstOrNull()
    }

    private fun resolveFromKtClass(klass: KtClass): Map<String, ResolvedPropertyDefinition> {
        val properties = linkedMapOf<String, ResolvedPropertyDefinition>()
        klass.primaryConstructorParameters.forEach { ctorParam ->
            if (!ctorParam.hasValOrVar()) return@forEach
            val propertyName = ctorParam.name ?: return@forEach
            val typeText = ctorParam.typeReference?.text ?: return@forEach
            properties[propertyName] = ResolvedPropertyDefinition(
                typeText = typeText,
                defaultValueText = ctorParam.defaultValue?.text,
            )
        }
        klass.declarations.filterIsInstance<KtProperty>().forEach { property ->
            val propertyName = property.name ?: return@forEach
            val typeText = property.typeReference?.text ?: return@forEach
            properties[propertyName] = ResolvedPropertyDefinition(
                typeText = typeText,
                defaultValueText = property.initializer?.text,
            )
        }
        return properties
    }

    private fun resolveFromPsiClass(klass: PsiClass): Map<String, ResolvedPropertyDefinition> {
        val properties = linkedMapOf<String, ResolvedPropertyDefinition>()
        klass.allFields.forEach { field ->
            val propertyName = field.name ?: return@forEach
            properties[propertyName] = ResolvedPropertyDefinition(typeText = field.type.presentableText)
        }
        return properties
    }

    private fun buildFlattenedParameterName(parameterName: String, propertyName: String): String {
        return parameterName + propertyName.replaceFirstChar { it.uppercase() }
    }

    private data class ResolvedPropertyDefinition(
        val typeText: String,
        val defaultValueText: String? = null,
    )
}

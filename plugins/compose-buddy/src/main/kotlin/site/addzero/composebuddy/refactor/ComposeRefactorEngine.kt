package site.addzero.composebuddy.refactor

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.analysis.ComposeComponentParameterRegistry
import site.addzero.composebuddy.analysis.ComposeForwardedParameter
import site.addzero.composebuddy.analysis.ComposeParameterSpec
import site.addzero.composebuddy.analysis.ComposeSignatureAnalysisResult
import site.addzero.composebuddy.analysis.ComposeStatePair
import site.addzero.composebuddy.analysis.ComposeWrapperAnalysisResult
import site.addzero.smart.intentions.core.SmartPsiWriteSupport

class ComposeRefactorEngine(
    private val project: Project,
) {
    fun expandWrapperSignature(result: ComposeWrapperAnalysisResult) {
        val function = result.function
        val factory = KtPsiFactory(project)
        SmartPsiWriteSupport.runWriteCommand(project, ComposeBuddyBundle.message("command.expand.wrapper")) {
            val currentParameterTexts = function.valueParameters.map { it.text }.toMutableList()
            currentParameterTexts += result.suggestedParameters.map { renderParameter(it.name, it.typeText, it.defaultValueText) }
            val newParameterList = "(${currentParameterTexts.joinToString(", ")})"
            val newCallText = rebuildWrapperCall(result)
            val oldParameterList = function.valueParameterList?.text ?: "()"
            val newFunctionText = function.text
                .replace(oldParameterList, newParameterList)
                .replace(result.targetCall.text, newCallText)
            function.replace(factory.createFunction(newFunctionText))
        }
    }

    fun generateWrapperProps(result: ComposeWrapperAnalysisResult) {
        val function = result.function
        val oldCallSites = collectCallSites(function)
        val oldParameterOrder = function.valueParameters.mapNotNull { it.name }
        val propsName = "${function.name}Props"
        val factory = KtPsiFactory(project)
        val targetSpecs = ComposeComponentParameterRegistry.parametersFor(result.targetName)
        val forwardedByParam = result.forwardedParameters.associateBy { it.parameter.name }

        SmartPsiWriteSupport.runWriteCommand(project, ComposeBuddyBundle.message("command.generate.wrapper.props")) {
            val oldParameterList = function.valueParameterList?.text ?: "()"
            val newParameterTexts = buildList {
                add("props: $propsName")
                addAll(result.extraParameters.map { it.text })
            }
            val newParameterList = "(${newParameterTexts.joinToString(", ")})"
            val newCallText = buildWrapperPropsCallText(result, "props", targetSpecs, forwardedByParam)
            val newFunctionText = function.text
                .replace(oldParameterList, newParameterList)
                .replace(result.targetCall.text, newCallText)
            val newFunction = function.replace(factory.createFunction(newFunctionText)) as KtNamedFunction

            insertDeclarationBefore(newFunction, renderDataClass(propsName, targetSpecs))
            updateWrapperPropsCallSites(
                oldCallSites = oldCallSites,
                oldParameterOrder = oldParameterOrder,
                extraParameters = result.extraParameters.mapNotNull { it.name },
                forwardedParameters = result.forwardedParameters,
                propsName = propsName,
            )
        }
    }

    fun normalizeSignature(
        analysis: ComposeSignatureAnalysisResult,
        request: ComposeRefactorRequest,
    ) {
        val function = analysis.function
        val factory = KtPsiFactory(project)
        val oldCallSites = collectCallSites(function)
        val oldParameters = function.valueParameters.toList()
        val movedToProps = if (request.extractProps) analysis.propsCandidates else emptyList()
        val movedToEvents = if (request.extractEvents) analysis.eventCandidates else emptyList()
        val movedToState = if (request.extractState) analysis.statePairs else emptyList()
        val movedParameters = buildSet<PsiElement> {
            addAll(movedToProps)
            addAll(movedToEvents)
            movedToState.forEach {
                add(it.valueParameter)
                add(it.callbackParameter)
            }
        }

        SmartPsiWriteSupport.runWriteCommand(project, ComposeBuddyBundle.message("command.normalize.signature")) {
            replaceParameterReferences(function, movedToProps, request.propsTypeName.lowercaseFirst())
            replaceParameterReferences(function, movedToEvents, request.eventsTypeName.lowercaseFirst())
            replaceStateReferences(function, movedToState, request.stateTypeName.lowercaseFirst())

            val newParameters = buildList {
                if (movedToProps.isNotEmpty()) add("${request.propsTypeName.lowercaseFirst()}: ${request.propsTypeName}")
                if (movedToState.isNotEmpty()) add("${request.stateTypeName.lowercaseFirst()}: ${request.stateTypeName}")
                if (movedToEvents.isNotEmpty()) add("${request.eventsTypeName.lowercaseFirst()}: ${request.eventsTypeName}")
                addAll(oldParameters.filterNot { it in movedParameters }.map { it.text })
            }
            val oldParameterList = function.valueParameterList?.text ?: "()"
            val newFunctionText = function.text.replace(oldParameterList, "(${newParameters.joinToString(", ")})")
            val newFunction = function.replace(factory.createFunction(newFunctionText)) as KtNamedFunction

            if (movedToProps.isNotEmpty()) {
                insertDeclarationBefore(newFunction, renderDataClass(request.propsTypeName, movedToProps.map(::specFromParameter)))
            }
            if (movedToEvents.isNotEmpty()) {
                insertDeclarationBefore(newFunction, renderDataClass(request.eventsTypeName, movedToEvents.map(::specFromParameter)))
            }
            if (movedToState.isNotEmpty()) {
                insertDeclarationBefore(newFunction, renderStateDataClass(request.stateTypeName, movedToState))
            }
            if (request.keepCompatibilityFunction) {
                insertDeclarationBefore(
                    newFunction,
                    renderCompatibilityFunction(
                        functionName = function.name ?: "Composable",
                        originalParameters = oldParameters,
                        request = request,
                        movedToProps = movedToProps,
                        movedToEvents = movedToEvents,
                        movedToState = movedToState,
                    ),
                )
            }

            updateNormalizedCallSites(
                oldCallSites = oldCallSites,
                functionName = function.name ?: return@runWriteCommand,
                oldParameters = oldParameters,
                request = request,
                movedToProps = movedToProps,
                movedToEvents = movedToEvents,
                movedToState = movedToState,
            )
        }
    }

    private fun rebuildWrapperCall(result: ComposeWrapperAnalysisResult): String {
        val existing = result.targetCall.valueArguments.map { it.text }.toMutableList()
        val existingNames = result.targetCall.valueArguments.mapNotNull { it.getArgumentName()?.asName?.identifier }.toSet()
        result.suggestedParameters.filterNot { it.name in existingNames }.forEach { spec ->
            existing += "${spec.name} = ${spec.name}"
        }
        return buildCallText(result.targetCall, existing, result.targetCall.lambdaArguments)
    }

    private fun buildWrapperPropsCallText(
        result: ComposeWrapperAnalysisResult,
        propsParameterName: String,
        targetSpecs: List<ComposeParameterSpec>,
        forwardedByParam: Map<String?, ComposeForwardedParameter>,
    ): String {
        val existingNames = result.targetCall.valueArguments.mapNotNull { it.getArgumentName()?.asName?.identifier }.toSet()
        val updated = result.targetCall.valueArguments.map { argument ->
            val targetName = argument.getArgumentName()?.asName?.identifier
            val expression = argument.getArgumentExpression()
            val forwarded = forwardedByParam[expression?.text]
            when {
                targetName != null && forwarded != null && forwarded.targetName == targetName -> "$targetName = $propsParameterName.$targetName"
                else -> argument.text
            }
        }.toMutableList()
        targetSpecs.filterNot { it.name in existingNames }.forEach { spec ->
            updated += "${spec.name} = $propsParameterName.${spec.name}"
        }
        return buildCallText(result.targetCall, updated, result.targetCall.lambdaArguments)
    }

    private fun renderDataClass(typeName: String, specs: List<ComposeParameterSpec>): String {
        return buildString {
            appendLine("data class $typeName(")
            appendLine(
                specs.joinToString(",\n") { spec ->
                    "    val ${spec.name}: ${spec.typeText}${spec.defaultValueText?.let { " = $it" } ?: ""}"
                }
            )
            appendLine(")")
        }
    }

    private fun renderStateDataClass(typeName: String, pairs: List<ComposeStatePair>): String {
        val specs = pairs.flatMap { pair -> listOf(specFromParameter(pair.valueParameter), specFromParameter(pair.callbackParameter)) }
        return renderDataClass(typeName, specs)
    }

    private fun renderCompatibilityFunction(
        functionName: String,
        originalParameters: List<KtParameter>,
        request: ComposeRefactorRequest,
        movedToProps: List<KtParameter>,
        movedToEvents: List<KtParameter>,
        movedToState: List<ComposeStatePair>,
    ): String {
        val forwardedCallArguments = buildList {
            if (movedToProps.isNotEmpty()) {
                add("${request.propsTypeName.lowercaseFirst()} = ${request.propsTypeName}(${movedToProps.joinToString(", ") { "${it.name} = ${it.name}" }})")
            }
            if (movedToState.isNotEmpty()) {
                val args = movedToState.flatMap { listOf(it.valueParameter, it.callbackParameter) }
                add("${request.stateTypeName.lowercaseFirst()} = ${request.stateTypeName}(${args.joinToString(", ") { "${it.name} = ${it.name}" }})")
            }
            if (movedToEvents.isNotEmpty()) {
                add("${request.eventsTypeName.lowercaseFirst()} = ${request.eventsTypeName}(${movedToEvents.joinToString(", ") { "${it.name} = ${it.name}" }})")
            }
            addAll(
                originalParameters
                    .filterNot { parameter ->
                        parameter in movedToProps || parameter in movedToEvents || movedToState.any {
                            it.valueParameter == parameter || it.callbackParameter == parameter
                        }
                    }
                    .mapNotNull { parameter -> parameter.name?.let { "$it = $it" } }
            )
        }
        return buildString {
            appendLine("@Deprecated(\"Use the normalized compose-buddy signature\")")
            appendLine("fun $functionName(${originalParameters.joinToString(", ") { it.text }}) {")
            appendLine("    $functionName(${forwardedCallArguments.joinToString(", ")})")
            appendLine("}")
        }
    }

    private fun replaceParameterReferences(function: KtNamedFunction, parameters: List<KtParameter>, containerName: String) {
        val factory = KtPsiFactory(project)
        parameters.forEach { parameter ->
            ReferencesSearch.search(parameter, LocalSearchScope(function.bodyExpression ?: function))
                .findAll()
                .mapNotNull { it.element as? KtNameReferenceExpression }
                .forEach { reference ->
                    reference.replace(factory.createExpression("$containerName.${parameter.name}"))
                }
        }
    }

    private fun replaceStateReferences(function: KtNamedFunction, statePairs: List<ComposeStatePair>, containerName: String) {
        val factory = KtPsiFactory(project)
        statePairs.flatMap { listOf(it.valueParameter, it.callbackParameter) }.forEach { parameter ->
            ReferencesSearch.search(parameter, LocalSearchScope(function.bodyExpression ?: function))
                .findAll()
                .mapNotNull { it.element as? KtNameReferenceExpression }
                .forEach { reference ->
                    reference.replace(factory.createExpression("$containerName.${parameter.name}"))
                }
        }
    }

    private fun insertDeclarationBefore(function: KtNamedFunction, declarationText: String) {
        val factory = KtPsiFactory(project)
        val parent = function.parent
        parent.addBefore(factory.createDeclaration(declarationText.trim()), function)
        parent.addBefore(factory.createWhiteSpace("\n\n"), function)
    }

    private fun collectCallSites(function: KtNamedFunction): List<KtCallExpression> {
        return ReferencesSearch.search(function, GlobalSearchScope.projectScope(project))
            .findAll()
            .mapNotNull { it.element.getStrictParentOfType<KtCallExpression>() }
            .distinctBy { Triple(it.containingFile.virtualFile?.path, it.textRange.startOffset, it.text) }
    }

    private fun updateWrapperPropsCallSites(
        oldCallSites: List<KtCallExpression>,
        oldParameterOrder: List<String>,
        extraParameters: List<String>,
        forwardedParameters: List<ComposeForwardedParameter>,
        propsName: String,
    ) {
        val movedMappings = forwardedParameters.associate { (it.parameter.name ?: it.targetName) to it.targetName }
        val factory = KtPsiFactory(project)
        oldCallSites.forEach { call ->
            val argumentMap = extractArgumentMap(call, oldParameterOrder)
            val propsAssignments = movedMappings.mapNotNull { (oldName, targetName) -> argumentMap[oldName]?.let { "$targetName = $it" } }
            val newArguments = buildList {
                add("props = $propsName(${propsAssignments.joinToString(", ")})")
                addAll(extraParameters.mapNotNull { extra -> argumentMap[extra]?.let { "$extra = $it" } })
            }
            val calleeText = call.calleeExpression?.text ?: return@forEach
            call.replace(factory.createExpression("$calleeText(${newArguments.joinToString(", ")})"))
        }
    }

    private fun updateNormalizedCallSites(
        oldCallSites: List<KtCallExpression>,
        functionName: String,
        oldParameters: List<KtParameter>,
        request: ComposeRefactorRequest,
        movedToProps: List<KtParameter>,
        movedToEvents: List<KtParameter>,
        movedToState: List<ComposeStatePair>,
    ) {
        val oldParameterOrder = oldParameters.mapNotNull { it.name }
        val remaining = oldParameters.filterNot { parameter ->
            parameter in movedToProps || parameter in movedToEvents || movedToState.any {
                it.valueParameter == parameter || it.callbackParameter == parameter
            }
        }
        val factory = KtPsiFactory(project)
        oldCallSites.forEach { call ->
            val argumentMap = extractArgumentMap(call, oldParameterOrder)
            val newArguments = buildList {
                if (movedToProps.isNotEmpty()) {
                    add("${request.propsTypeName.lowercaseFirst()} = ${request.propsTypeName}(${movedToProps.mapNotNull { parameter -> parameter.name?.let { name -> argumentMap[name]?.let { "$name = $it" } } }.joinToString(", ")})")
                }
                if (movedToState.isNotEmpty()) {
                    val stateArgs = movedToState.flatMap { listOf(it.valueParameter, it.callbackParameter) }
                    add("${request.stateTypeName.lowercaseFirst()} = ${request.stateTypeName}(${stateArgs.mapNotNull { parameter -> parameter.name?.let { name -> argumentMap[name]?.let { "$name = $it" } } }.joinToString(", ")})")
                }
                if (movedToEvents.isNotEmpty()) {
                    add("${request.eventsTypeName.lowercaseFirst()} = ${request.eventsTypeName}(${movedToEvents.mapNotNull { parameter -> parameter.name?.let { name -> argumentMap[name]?.let { "$name = $it" } } }.joinToString(", ")})")
                }
                addAll(remaining.mapNotNull { parameter -> parameter.name?.let { name -> argumentMap[name]?.let { "$name = $it" } } })
            }
            call.replace(factory.createExpression("$functionName(${newArguments.joinToString(", ")})"))
        }
    }

    private fun buildCallText(call: KtCallExpression, arguments: List<String>, lambdaArguments: List<KtLambdaArgument>): String {
        val calleeText = call.calleeExpression?.text ?: return call.text
        val lambdaSuffix = if (lambdaArguments.isEmpty()) "" else lambdaArguments.joinToString(" ") { it.text }
        return "$calleeText(${arguments.joinToString(", ")})$lambdaSuffix"
    }

    private fun extractArgumentMap(call: KtCallExpression, parameterOrder: List<String>): Map<String, String> {
        val values = linkedMapOf<String, String>()
        var positionalIndex = 0
        call.valueArguments.forEach { argument ->
            val expressionText = argument.getArgumentExpression()?.text ?: return@forEach
            val explicitName = argument.getArgumentName()?.asName?.identifier
            if (explicitName != null) {
                values[explicitName] = expressionText
                return@forEach
            }
            while (positionalIndex < parameterOrder.size && parameterOrder[positionalIndex] in values) {
                positionalIndex++
            }
            if (positionalIndex < parameterOrder.size) {
                values[parameterOrder[positionalIndex]] = expressionText
                positionalIndex++
            }
        }
        call.lambdaArguments.firstOrNull()?.getLambdaExpression()?.text?.let { lambdaText ->
            while (positionalIndex < parameterOrder.size && parameterOrder[positionalIndex] in values) {
                positionalIndex++
            }
            if (positionalIndex < parameterOrder.size) {
                values[parameterOrder[positionalIndex]] = lambdaText
            }
        }
        return values
    }

    private fun specFromParameter(parameter: KtParameter): ComposeParameterSpec {
        return ComposeParameterSpec(
            name = parameter.name ?: "value",
            typeText = parameter.typeReference?.text ?: "Any",
            defaultValueText = parameter.defaultValue?.text,
        )
    }

    private fun renderParameter(name: String, typeText: String, defaultValueText: String?): String {
        return "$name: $typeText${defaultValueText?.let { " = $it" } ?: ""}"
    }
}

private fun String.lowercaseFirst(): String = replaceFirstChar { it.lowercase() }

package site.addzero.composebuddy.features.viewmodelinline

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.support.ComposeCallSupport
import site.addzero.composebuddy.support.ComposeRefactorWriteSupport

class ViewModelInlineRefactor(
    private val project: Project,
) {
    fun apply(analysis: ViewModelInlineAnalysisResult) {
        val function = analysis.function
        val factory = KtPsiFactory(project)
        val parameterOrder = function.valueParameters.mapNotNull { it.name }
        val callSites = ComposeCallSupport.collectCallSites(project, function)
        val parameterCandidates = analysis.candidates.filter { it.parameter != null }
        val localCandidates = analysis.candidates.filter { it.localProperty != null }
        val candidateByName = parameterCandidates.associateBy { it.parameter?.name }
        val allCandidateByName = analysis.candidates.associateBy { it.parameter?.name ?: it.localProperty?.name }
        ComposeRefactorWriteSupport.run(project, ComposeBuddyBundle.message("command.inline.used.viewmodel.members")) {
            insertLocalBindings(function, localCandidates, factory)
            rewriteFunctionBody(function, allCandidateByName, factory)
            if (parameterCandidates.isNotEmpty()) {
                val newParameters = buildList {
                    function.valueParameters.forEach { parameter ->
                        val parameterName = parameter.name ?: return@forEach
                        val candidate = candidateByName[parameterName]
                        if (candidate == null) {
                            add(parameter.text)
                            return@forEach
                        }
                        if (candidate.keepOriginalParameter) {
                            add(parameter.text)
                        }
                        candidate.states.forEach { state ->
                            add("${state.parameterName}: ${state.typeText}")
                        }
                        candidate.events.forEach { event ->
                            val functionType = buildString {
                                append("(")
                                append(event.parameterTypes.joinToString(", "))
                                append(") -> ")
                                append(event.returnType)
                            }
                            add("${event.parameterName}: $functionType")
                        }
                    }
                }
                val oldParameterList = function.valueParameterList?.text ?: "()"
                function.replace(factory.createFunction(function.text.replace(oldParameterList, "(${newParameters.joinToString(", ")})")))
                updateCallSites(function.name ?: return@run, callSites, parameterOrder, candidateByName, factory)
            }
        }
    }

    private fun insertLocalBindings(
        function: KtNamedFunction,
        localCandidates: List<ViewModelInlineCandidate>,
        factory: KtPsiFactory,
    ) {
        if (localCandidates.isEmpty()) return
        val body = function.bodyExpression as? KtBlockExpression ?: return
        localCandidates.forEach { candidate ->
            val property = candidate.localProperty ?: return@forEach
            val receiverName = property.name ?: return@forEach
            val bindingsText = buildString {
                candidate.states.forEach { state ->
                    append("val ")
                    append(state.parameterName)
                    append(" = ")
                    append(receiverName)
                    append(".")
                    append(state.pathSegments.joinToString("."))
                    append("\n")
                }
                candidate.events.forEach { event ->
                    append("val ")
                    append(event.parameterName)
                    append(" = { ")
                    if (event.parameterTypes.isNotEmpty()) {
                        val lambdaParams = event.parameterTypes.indices.joinToString(", ") { index -> "arg$index" }
                        append(lambdaParams)
                        append(" -> ")
                        append(receiverName)
                        if (event.receiverPathSegments.isNotEmpty()) {
                            append(".")
                            append(event.receiverPathSegments.joinToString("."))
                        }
                        append(".")
                        append(event.name)
                        append("(")
                        append(event.parameterTypes.indices.joinToString(", ") { index -> "arg$index" })
                        append(") }")
                    } else {
                        append(receiverName)
                        if (event.receiverPathSegments.isNotEmpty()) {
                            append(".")
                            append(event.receiverPathSegments.joinToString("."))
                        }
                        append(".")
                        append(event.name)
                        append("() }")
                    }
                    append("\n")
                }
            }.trim()
            if (bindingsText.isBlank()) return@forEach
            val block = factory.createBlock("{\n$bindingsText\n}")
            val statements = block.statements
            if (statements.isEmpty()) return@forEach
            body.addRangeAfter(statements.first(), statements.last(), property)
            if (!candidate.keepOriginalParameter) {
                property.delete()
            }
        }
    }

    private fun rewriteFunctionBody(
        function: KtNamedFunction,
        candidateByName: Map<String?, ViewModelInlineCandidate>,
        factory: KtPsiFactory,
    ) {
        val body = function.bodyExpression ?: return
        body.collectDescendantsOfType<KtDotQualifiedExpression>()
            .sortedByDescending { it.textRange.startOffset }
            .forEach { expression ->
                val chain = collectAccessChain(expression) ?: return@forEach
                val candidate = candidateByName[chain.segments.firstOrNull()] ?: return@forEach
                when {
                    !chain.isCall -> {
                        val state = candidate.states.firstOrNull { it.pathSegments == chain.segments.drop(1) } ?: return@forEach
                        expression.replace(factory.createExpression(state.parameterName))
                    }
                    else -> {
                        val eventPath = chain.segments.drop(1)
                        val event = candidate.events.firstOrNull {
                            it.receiverPathSegments + it.name == eventPath
                        } ?: return@forEach
                        val selector = expression.selectorExpression as? KtCallExpression ?: return@forEach
                        val args = selector.valueArguments.joinToString(", ") { it.text }
                        expression.replace(factory.createExpression("${event.parameterName}($args)"))
                    }
                }
            }
    }

    private fun updateCallSites(
        functionName: String,
        callSites: List<KtCallExpression>,
        parameterOrder: List<String>,
        candidateByName: Map<String?, ViewModelInlineCandidate>,
        factory: KtPsiFactory,
    ) {
        callSites.forEach { call ->
            val argumentMap = ComposeCallSupport.extractArgumentMap(call, parameterOrder)
            val newArguments = buildList {
                parameterOrder.forEach { parameterName ->
                    val candidate = candidateByName[parameterName]
                    if (candidate == null) {
                        argumentMap[parameterName]?.let { add("$parameterName = $it") }
                        return@forEach
                    }
                    val receiver = argumentMap[parameterName] ?: parameterName
                    if (candidate.keepOriginalParameter) {
                        add("$parameterName = $receiver")
                    }
                    candidate.states.forEach { state ->
                        add("${state.parameterName} = ${wrapReceiver(receiver)}.${state.pathSegments.joinToString(".")}")
                    }
                    candidate.events.forEach { event ->
                        val lambdaParams = event.parameterTypes.indices.joinToString(", ") { index -> "arg$index" }
                        val invocation = event.parameterTypes.indices.joinToString(", ") { index -> "arg$index" }
                        val target = buildString {
                            append(wrapReceiver(receiver))
                            if (event.receiverPathSegments.isNotEmpty()) {
                                append(".")
                                append(event.receiverPathSegments.joinToString("."))
                            }
                            append(".")
                            append(event.name)
                        }
                        add("${event.parameterName} = { $lambdaParams -> $target($invocation) }")
                    }
                }
            }
            call.replace(factory.createExpression("$functionName(${newArguments.joinToString(", ")})"))
        }
    }

    private fun wrapReceiver(text: String): String {
        return if (text.matches(Regex("[A-Za-z_][A-Za-z0-9_\\.]*"))) {
            text
        } else {
            "($text)"
        }
    }

    private fun collectAccessChain(expression: KtDotQualifiedExpression): RefactorAccessChain? {
        fun visit(node: org.jetbrains.kotlin.psi.KtExpression): RefactorAccessChain? {
            return when (node) {
                is KtDotQualifiedExpression -> {
                    val receiver = visit(node.receiverExpression) ?: return null
                    when (val selector = node.selectorExpression) {
                        is org.jetbrains.kotlin.psi.KtNameReferenceExpression -> RefactorAccessChain(receiver.segments + selector.getReferencedName(), false)
                        is KtCallExpression -> {
                            val callee = selector.calleeExpression?.text ?: return null
                            RefactorAccessChain(receiver.segments + callee, true)
                        }
                        else -> null
                    }
                }
                is org.jetbrains.kotlin.psi.KtNameReferenceExpression -> RefactorAccessChain(listOf(node.getReferencedName()), false)
                else -> null
            }
        }
        return visit(expression)
    }
}

private data class RefactorAccessChain(
    val segments: List<String>,
    val isCall: Boolean,
)

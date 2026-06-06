package site.addzero.composebuddy.features.effectkeys

import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import site.addzero.composebuddy.support.ComposePsiSupport

data class EffectKeysIssue(
    val call: KtCallExpression,
    val suggestedKeys: List<String>,
)

object EffectKeysAnalysis {
    private val effectCalls = setOf("LaunchedEffect", "DisposableEffect")
    private val effectBodyArgumentNames = setOf("block", "effect")
    private val stateHolderTypeSuffixes = setOf(
        "ScaffoldState",
        "ListState",
        "GridState",
        "ScrollState",
        "PagerState",
        "DrawerState",
        "SheetState",
        "HostState",
        "ViewModel",
    )

    fun analyze(function: KtNamedFunction): List<EffectKeysIssue> {
        if (!ComposePsiSupport.isComposable(function)) return emptyList()
        return function.collectDescendantsOfType<KtCallExpression>().mapNotNull { call ->
            val name = call.calleeExpression?.text ?: return@mapNotNull null
            if (name !in effectCalls) return@mapNotNull null
            call.lambdaArguments.firstOrNull() ?: return@mapNotNull null
            val captured = capturedRestartKeyParameterNames(call, function)
            if (captured.isEmpty()) return@mapNotNull null
            val actualKeys = effectKeyArguments(call)
            if (actualKeys != captured) {
                EffectKeysIssue(call, captured)
            } else {
                null
            }
        }
    }

    private fun effectKeyArguments(call: KtCallExpression): List<String> {
        return call.valueArguments.mapNotNull { argument ->
            val name = argument.getArgumentName()?.asName?.identifier
            if (name in effectBodyArgumentNames) {
                return@mapNotNull null
            }
            val expression = argument.getArgumentExpression()
            if (expression is KtLambdaExpression) {
                return@mapNotNull null
            }
            (expression?.text ?: argument.text).toRestartKeyText()
        }
    }

    private fun capturedRestartKeyParameterNames(call: KtCallExpression, function: KtNamedFunction): List<String> {
        val keyCandidates = function.valueParameters
            .filterNot(::isNonRestartKeyParameter)
            .mapNotNull { parameter -> parameter.name?.let { name -> name to parameter } }
        val keyCandidateByName = keyCandidates.toMap()
        val referencedNames = linkedSetOf<String>()
        call.lambdaArguments
            .flatMap { lambda -> lambda.collectDescendantsOfType<KtNameReferenceExpression>() }
            .forEach { reference ->
                val name = reference.getReferencedName()
                val parameter = keyCandidateByName[name] ?: return@forEach
                if (reference.resolvesTo(parameter)) {
                    referencedNames += name
                }
            }
        return keyCandidates.mapNotNull { (name, _) -> name.takeIf { it in referencedNames } }
    }

    private fun KtNameReferenceExpression.resolvesTo(parameter: KtParameter): Boolean {
        val resolved = runCatching { mainReference.resolve() }.getOrNull()
        return resolved == null || resolved == parameter
    }

    private fun isNonRestartKeyParameter(parameter: KtParameter): Boolean {
        val typeText = parameter.typeReference?.text.orEmpty()
        if (typeText.contains("->")) {
            return true
        }
        return isRememberedStateHolderParameter(parameter)
    }

    private fun isRememberedStateHolderParameter(parameter: KtParameter): Boolean {
        val typeName = parameter.typeReference?.text
            ?.substringAfterLast('.')
            ?.substringBefore('<')
            ?.trim()
            ?.trimEnd('?')
            ?: return false
        if (stateHolderTypeSuffixes.any { suffix -> typeName.endsWith(suffix) }) {
            return true
        }
        val defaultValue = parameter.defaultValue?.text?.trim() ?: return false
        return typeName.endsWith("State") &&
            defaultValue.startsWith("remember") &&
            defaultValue.contains("State(")
    }

    private fun String.toRestartKeyText(): String {
        return replace(Regex("^key\\d+\\s*=\\s*"), "")
            .replace(Regex("^keys\\s*=\\s*"), "")
            .trim()
    }
}

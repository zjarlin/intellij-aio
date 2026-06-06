package site.addzero.composebuddy.features.callargextract

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import site.addzero.composebuddy.support.ComposePsiSupport

data class CallArgExtractCandidate(
    val argumentName: String,
    val typeText: String,
    val defaultValueText: String,
)

data class CallArgExtractAnalysisResult(
    val function: KtNamedFunction,
    val call: KtCallExpression,
    val candidates: List<CallArgExtractCandidate>,
)

object CallArgExtractAnalysis {
    fun analyzeSingle(element: PsiElement): CallArgExtractAnalysisResult? {
        val argument = element.getStrictParentOfType<KtValueArgument>() ?: return null
        val call = argument.getStrictParentOfType<KtCallExpression>() ?: return null
        return analyze(call) { it == argument }
    }

    fun analyzeBatch(element: PsiElement): CallArgExtractAnalysisResult? {
        val call = element.getStrictParentOfType<KtCallExpression>() ?: return null
        return analyze(call) { true }
    }

    private fun analyze(
        call: KtCallExpression,
        predicate: (KtValueArgument) -> Boolean,
    ): CallArgExtractAnalysisResult? {
        val function = call.getStrictParentOfType<KtNamedFunction>() ?: return null
        if (!ComposePsiSupport.isComposable(function)) return null
        val existingParameterNames = function.valueParameters.mapNotNull { it.name }.toSet()
        val signature = resolveParameterTypes(call)
        val candidates = call.valueArguments.mapNotNull { argument ->
            if (!predicate(argument)) return@mapNotNull null
            val argumentName = argument.getArgumentName()?.asName?.identifier ?: return@mapNotNull null
            if (argumentName in existingParameterNames) return@mapNotNull null
            if (!isPlaceholder(argument)) return@mapNotNull null
            val typeText = signature[argumentName] ?: "Any"
            val defaultValueText = argument.getArgumentExpression()?.text ?: "TODO()"
            CallArgExtractCandidate(
                argumentName = argumentName,
                typeText = typeText,
                defaultValueText = defaultValueText,
            )
        }
        if (candidates.isEmpty()) return null
        return CallArgExtractAnalysisResult(function, call, candidates)
    }

    private fun isPlaceholder(argument: KtValueArgument): Boolean {
        val expression = argument.getArgumentExpression() ?: return true
        val text = expression.text.trim()
        if (text == "null" || text == "\"\"") {
            return true
        }
        return text.matches(Regex("""TODO\s*\(.*\)"""))
    }

    private fun resolveParameterTypes(call: KtCallExpression): Map<String, String> {
        val target = call.calleeExpression?.mainReference?.resolve()
        return when (target) {
            is KtNamedFunction -> target.valueParameters.mapNotNull { parameter ->
                val name = parameter.name ?: return@mapNotNull null
                name to (parameter.typeReference?.text ?: "Any")
            }.toMap()

            else -> emptyMap()
        }
    }
}

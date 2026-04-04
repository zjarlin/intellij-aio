package site.addzero.composebuddy.features.callargfill

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import site.addzero.composebuddy.support.ComposePsiSupport

data class CallArgFillReplacement(
    val argumentName: String,
)

data class CallArgFillAnalysisResult(
    val function: KtNamedFunction,
    val call: KtCallExpression,
    val replacements: List<CallArgFillReplacement>,
)

object CallArgFillAnalysis {
    fun analyze(call: KtCallExpression): CallArgFillAnalysisResult? {
        val function = call.getStrictParentOfType<KtNamedFunction>() ?: return null
        if (!ComposePsiSupport.isComposable(function)) return null
        val parameterNames = function.valueParameters.mapNotNull { it.name }.toSet()
        if (parameterNames.isEmpty()) return null
        val replacements = call.valueArguments.mapNotNull { argument ->
            val argumentName = argument.getArgumentName()?.asName?.identifier ?: return@mapNotNull null
            if (argumentName !in parameterNames) return@mapNotNull null
            if (!isPlaceholder(argument)) return@mapNotNull null
            CallArgFillReplacement(argumentName)
        }
        if (replacements.isEmpty()) return null
        return CallArgFillAnalysisResult(
            function = function,
            call = call,
            replacements = replacements,
        )
    }

    private fun isPlaceholder(argument: KtValueArgument): Boolean {
        val expression = argument.getArgumentExpression() ?: return true
        val text = expression.text.trim()
        if (text == "null" || text == "\"\"") {
            return true
        }
        return text.matches(Regex("""TODO\s*\(.*\)"""))
    }
}

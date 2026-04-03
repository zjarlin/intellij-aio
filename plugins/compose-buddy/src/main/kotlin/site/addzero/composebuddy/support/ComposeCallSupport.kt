package site.addzero.composebuddy.support

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

object ComposeCallSupport {
    fun collectCallSites(project: Project, function: KtNamedFunction): List<KtCallExpression> {
        return ReferencesSearch.search(function, GlobalSearchScope.projectScope(project))
            .findAll()
            .mapNotNull { it.element.getStrictParentOfType<KtCallExpression>() }
            .distinctBy { Triple(it.containingFile.virtualFile?.path, it.textRange.startOffset, it.text) }
    }

    fun extractArgumentMap(call: KtCallExpression, parameterOrder: List<String>): Map<String, String> {
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

    fun buildCallText(
        call: KtCallExpression,
        arguments: List<String>,
        lambdaArguments: List<KtLambdaArgument> = call.lambdaArguments,
    ): String {
        val calleeText = call.calleeExpression?.text ?: return call.text
        val lambdaSuffix = if (lambdaArguments.isEmpty()) "" else lambdaArguments.joinToString(" ") { it.text }
        return "$calleeText(${arguments.joinToString(", ")})$lambdaSuffix"
    }
}

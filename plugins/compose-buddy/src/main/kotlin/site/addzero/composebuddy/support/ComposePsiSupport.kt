package site.addzero.composebuddy.support

import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

object ComposePsiSupport {
    fun isComposable(function: KtNamedFunction): Boolean {
        return function.annotationEntries.any { entry ->
            entry.shortName?.asString() == "Composable" || entry.text.endsWith(".Composable")
        }
    }

    fun findPrimaryCall(function: KtNamedFunction): KtCallExpression? {
        val body = function.bodyExpression ?: return null
        return when (body) {
            is KtCallExpression -> body
            is KtDotQualifiedExpression -> body.selectorExpression as? KtCallExpression
            is KtBlockExpression -> {
                val statements = body.statements
                if (statements.size != 1) {
                    null
                } else {
                    val single = statements.single()
                    when (single) {
                        is KtCallExpression -> single
                        is KtDotQualifiedExpression -> single.selectorExpression as? KtCallExpression
                        else -> single.collectDescendantsOfType<KtCallExpression>()
                            .firstOrNull { it.getStrictParentOfType<KtNamedFunction>() == function }
                    }
                }
            }

            else -> body.collectDescendantsOfType<KtCallExpression>()
                .firstOrNull { it.getStrictParentOfType<KtNamedFunction>() == function }
        }
    }

    fun layoutContainerNames(): Set<String> {
        return setOf("Box", "Column", "Row", "LazyColumn", "LazyRow")
    }

    fun findTopLevelLayoutChildren(function: KtNamedFunction): List<KtCallExpression> {
        val primary = findPrimaryCall(function) ?: return emptyList()
        val lambda = primary.lambdaArguments.firstOrNull()?.getLambdaExpression()?.bodyExpression ?: return emptyList()
        return lambda.statements.mapNotNull { statement ->
            when (statement) {
                is KtCallExpression -> statement
                is KtDotQualifiedExpression -> statement.selectorExpression as? KtCallExpression
                else -> null
            }
        }
    }

    fun selectedRangeContains(selectionStart: Int, selectionEnd: Int, expression: KtExpression): Boolean {
        return expression.textRange.startOffset >= selectionStart && expression.textRange.endOffset <= selectionEnd
    }
}

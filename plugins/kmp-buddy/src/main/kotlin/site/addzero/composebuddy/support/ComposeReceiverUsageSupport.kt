package site.addzero.composebuddy.support

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

object ComposeReceiverUsageSupport {
    fun usesImplicitReceiver(elements: List<PsiElement>): Boolean {
        return elements.any(::usesImplicitReceiver)
    }

    private fun usesImplicitReceiver(element: PsiElement): Boolean {
        if (element is KtThisExpression) {
            return true
        }
        if (collectCalls(element).any(::requiresImplicitReceiver)) {
            return true
        }
        return element.collectDescendantsOfType<KtThisExpression>().isNotEmpty()
    }

    private fun collectCalls(element: PsiElement): Sequence<KtCallExpression> {
        return sequence {
            if (element is KtCallExpression) {
                yield(element)
            }
            yieldAll(element.collectDescendantsOfType<KtCallExpression>().asSequence())
        }
    }

    private fun requiresImplicitReceiver(call: KtCallExpression): Boolean {
        val resolved = call.calleeExpression?.mainReference?.resolve() as? KtNamedFunction ?: return false
        val hasExplicitReceiver = (call.parent as? KtQualifiedExpression)?.selectorExpression == call
        if (resolved.receiverTypeReference != null && !hasExplicitReceiver) {
            return true
        }
        val declaredInClass = resolved.getStrictParentOfType<KtClassOrObject>() != null
        return declaredInClass && (!hasExplicitReceiver || resolved.receiverTypeReference != null)
    }
}

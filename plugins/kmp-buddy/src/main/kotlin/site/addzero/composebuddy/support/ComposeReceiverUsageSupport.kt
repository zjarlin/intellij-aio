package site.addzero.composebuddy.support

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind

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
        val resolvedCall = runCatching { call.resolveToCall() }.getOrNull() ?: return false
        val explicitReceiverKind = resolvedCall.explicitReceiverKind
        val hasImplicitDispatchReceiver = resolvedCall.dispatchReceiver != null &&
            explicitReceiverKind != ExplicitReceiverKind.DISPATCH_RECEIVER &&
            explicitReceiverKind != ExplicitReceiverKind.BOTH_RECEIVERS
        val hasImplicitExtensionReceiver = resolvedCall.extensionReceiver != null &&
            explicitReceiverKind == ExplicitReceiverKind.NO_EXPLICIT_RECEIVER
        return hasImplicitDispatchReceiver || hasImplicitExtensionReceiver
    }
}

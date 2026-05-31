package site.addzero.smart.intentions.kotlin.kotlinxjson

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument

internal object KotlinxJsonExplicitSerializerSupport {
    fun isApplicable(call: KtCallExpression): Boolean {
        return buildReplacement(call) != null
    }

    fun apply(call: KtCallExpression) {
        val replacementText = buildReplacement(call) ?: return
        val replacement = KtPsiFactory(call.project).createExpression(replacementText)
        call.replace(replacement)
    }

    private fun buildReplacement(call: KtCallExpression): String? {
        val calleeName = call.calleeExpression?.text ?: return null
        if (calleeName !in supportedCallees) {
            return null
        }

        val arguments = call.valueArguments
        if (arguments.size != 2 || arguments.any { argument -> argument.getArgumentName() != null }) {
            return null
        }

        if (!arguments[0].isExplicitSerializerCall()) {
            return null
        }
        val payloadText = arguments[1].getArgumentExpression()?.text?.takeIf { it.isNotBlank() } ?: return null
        return "$calleeName($payloadText)"
    }

    private fun KtValueArgument.isExplicitSerializerCall(): Boolean {
        val expression = getArgumentExpression() ?: return false
        val qualifiedExpression = expression as? KtDotQualifiedExpression ?: return false
        val selectorCall = qualifiedExpression.selectorExpression as? KtCallExpression ?: return false
        if (selectorCall.calleeExpression?.text != "serializer") {
            return false
        }
        if (selectorCall.valueArguments.isNotEmpty() || selectorCall.typeArguments.isNotEmpty()) {
            return false
        }
        return qualifiedExpression.receiverExpression.isStableSerializerReceiver()
    }

    private fun KtExpression.isStableSerializerReceiver(): Boolean {
        return text
            .trim()
            .let { candidate -> candidate.isNotBlank() && '\n' !in candidate && '\r' !in candidate }
    }

    private val supportedCallees = setOf("encodeToString", "decodeFromString")
}

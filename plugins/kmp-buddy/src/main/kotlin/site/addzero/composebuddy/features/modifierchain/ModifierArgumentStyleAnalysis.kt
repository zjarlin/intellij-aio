package site.addzero.composebuddy.features.modifierchain

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import site.addzero.composebuddy.support.ComposePsiSupport

internal data class ModifierArgumentStyleAnalysisResult(
    val function: KtNamedFunction,
    val argument: KtValueArgument,
    val rootModifierText: String,
    val calls: List<String>,
)

internal object ModifierArgumentStyleAnalysis {
    fun analyze(element: PsiElement): ModifierArgumentStyleAnalysisResult? {
        val argument = element.getStrictParentOfType<KtValueArgument>() ?: return null
        if (argument.getArgumentName()?.asName?.identifier != "modifier") {
            return null
        }
        val call = argument.getStrictParentOfType<KtCallExpression>() ?: return null
        val function = call.getStrictParentOfType<KtNamedFunction>() ?: return null
        if (!ComposePsiSupport.isComposable(function)) {
            return null
        }
        val expression = argument.getArgumentExpression() ?: return null
        val chain = collectModifierChainCalls(expression) ?: return null
        if (chain.calls.size < 2) {
            return null
        }
        return ModifierArgumentStyleAnalysisResult(
            function = function,
            argument = argument,
            rootModifierText = chain.rootModifierText,
            calls = chain.calls,
        )
    }

    private fun collectModifierChainCalls(expression: KtExpression): ModifierChain? {
        val calls = mutableListOf<String>()
        var current: KtExpression? = expression
        while (current is KtDotQualifiedExpression) {
            val selector = current.selectorExpression as? KtCallExpression ?: return null
            calls += selector.text.trim().trimIndent()
            current = current.receiverExpression
        }
        val rootText = current?.text?.trim() ?: return null
        if (rootText != "modifier" && rootText != "Modifier") {
            return null
        }
        return ModifierChain(
            rootModifierText = rootText,
            calls = calls.asReversed(),
        )
    }

    private data class ModifierChain(
        val rootModifierText: String,
        val calls: List<String>,
    )
}

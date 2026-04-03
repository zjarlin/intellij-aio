package site.addzero.composebuddy.features.modifierchain

import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import site.addzero.composebuddy.support.ComposePsiSupport

data class ModifierChainAnalysisResult(
    val file: KtFile,
    val function: KtNamedFunction,
    val chainText: String,
    val occurrences: List<KtDotQualifiedExpression>,
)

object ModifierChainAnalysis {
    fun analyze(function: KtNamedFunction): ModifierChainAnalysisResult? {
        if (!ComposePsiSupport.isComposable(function)) return null
        val file = function.containingKtFile
        val modifierChains = file.collectDescendantsOfType<KtDotQualifiedExpression>()
            .filter { it.text.startsWith("Modifier.") }
            .groupBy { it.text }
            .entries
            .filter { it.key.count { ch -> ch == '.' } >= 2 && it.value.size >= 2 }
            .maxByOrNull { it.value.size }
            ?: return null
        return ModifierChainAnalysisResult(file, function, modifierChains.key, modifierChains.value)
    }
}

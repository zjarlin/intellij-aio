package site.addzero.composebuddy.features.deletecomponent

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import site.addzero.composebuddy.support.ComposePsiSupport

data class DeleteComposeCallAndOrphanLocalComponentsAnalysisResult(
    val file: KtFile,
    val callStatements: List<KtExpression>,
    val targetFunctions: List<KtNamedFunction>,
    val orphanFunctions: List<KtNamedFunction>,
)

private data class DeleteComposeCallTarget(
    val statement: KtExpression,
    val targetFunction: KtNamedFunction,
)

object DeleteComposeCallAndOrphanLocalComponentsAnalysis {
    fun analyze(
        editor: Editor?,
        element: PsiElement,
    ): DeleteComposeCallAndOrphanLocalComponentsAnalysisResult? {
        editor ?: return null
        if (editor.selectionModel.hasSelection()) {
            return analyzeSelection(editor, element)
        }
        val call = element.getStrictParentOfType<KtCallExpression>() ?: return null
        val callStatement = standaloneStatementFor(call) ?: return null
        val targetFunction = resolveSameFilePrivateFunction(call) ?: return null
        if (!ComposePsiSupport.isComposable(targetFunction)) {
            return null
        }
        return buildResult(
            file = targetFunction.containingKtFile,
            targets = listOf(DeleteComposeCallTarget(callStatement, targetFunction)),
        )
    }

    private fun analyzeSelection(
        editor: Editor,
        element: PsiElement,
    ): DeleteComposeCallAndOrphanLocalComponentsAnalysisResult? {
        val file = element.containingFile as? KtFile ?: return null
        val selectionRange = normalizeSelectionRange(
            text = editor.document.charsSequence,
            range = TextRange(editor.selectionModel.selectionStart, editor.selectionModel.selectionEnd),
        )
        val targets = file.collectDescendantsOfType<KtBlockExpression>()
            .mapNotNull { block -> collectSelectedTargets(block, selectionRange) }
            .minByOrNull { blockTargets -> blockTargets.first().statement.textRange.length }
            ?: return null
        return buildResult(file, targets)
    }

    private fun collectSelectedTargets(
        block: KtBlockExpression,
        selectionRange: TextRange,
    ): List<DeleteComposeCallTarget>? {
        val selectedStatements = block.statements
            .filter { statement -> containsElement(selectionRange, statement) }
        if (selectedStatements.isEmpty() || !statementsSpanSelection(selectedStatements, selectionRange)) {
            return null
        }
        val targets = selectedStatements.map { statement ->
            deleteTargetFor(statement) ?: return null
        }
        return targets.takeIf { it.isNotEmpty() }
    }

    private fun buildResult(
        file: KtFile,
        targets: List<DeleteComposeCallTarget>,
    ): DeleteComposeCallAndOrphanLocalComponentsAnalysisResult {
        val callStatements = targets
            .map { target -> target.statement }
            .distinctBy { statement -> statement.textRange.startOffset }
        val targetFunctions = targets
            .map { target -> target.targetFunction }
            .distinctBy { function -> function.textRange.startOffset }
        val candidates = targetFunctions
            .flatMap { function -> collectReachablePrivateFunctions(function) }
            .distinctBy { function -> function.textRange.startOffset }
        val orphanFunctions = collectOrphanFunctions(
            file = file,
            candidates = candidates,
            removedStatements = callStatements,
        )
        return DeleteComposeCallAndOrphanLocalComponentsAnalysisResult(
            file = file,
            callStatements = callStatements,
            targetFunctions = targetFunctions,
            orphanFunctions = orphanFunctions,
        )
    }

    private fun standaloneStatementFor(call: KtCallExpression): KtExpression? {
        val parent = call.parent
        if (parent is KtBlockExpression && parent.statements.contains(call)) {
            return call
        }
        if (parent is KtDotQualifiedExpression && parent.selectorExpression == call) {
            val grandParent = parent.parent
            if (grandParent is KtBlockExpression && grandParent.statements.contains(parent)) {
                return parent
            }
        }
        return null
    }

    private fun deleteTargetFor(statement: KtExpression): DeleteComposeCallTarget? {
        val call = when (statement) {
            is KtCallExpression -> statement
            is KtDotQualifiedExpression -> statement.selectorExpression as? KtCallExpression
            else -> null
        } ?: return null
        val targetFunction = resolveSameFilePrivateFunction(call) ?: return null
        if (!ComposePsiSupport.isComposable(targetFunction)) {
            return null
        }
        return DeleteComposeCallTarget(statement, targetFunction)
    }

    private fun normalizeSelectionRange(
        text: CharSequence,
        range: TextRange,
    ): TextRange {
        var start = range.startOffset
        var end = range.endOffset
        while (start < end && text[start].isWhitespace()) {
            start++
        }
        while (end > start && text[end - 1].isWhitespace()) {
            end--
        }
        return TextRange(start, end)
    }

    private fun containsElement(range: TextRange, element: PsiElement): Boolean {
        return element.textRange.startOffset >= range.startOffset &&
            element.textRange.endOffset <= range.endOffset
    }

    private fun statementsSpanSelection(
        statements: List<PsiElement>,
        selectionRange: TextRange,
    ): Boolean {
        val first = statements.firstOrNull() ?: return false
        val last = statements.lastOrNull() ?: return false
        return first.textRange.startOffset <= selectionRange.startOffset &&
            last.textRange.endOffset >= selectionRange.endOffset
    }

    private fun resolveSameFilePrivateFunction(call: KtCallExpression): KtNamedFunction? {
        val resolved = runCatching { call.calleeExpression?.mainReference?.resolve() }.getOrNull()
        val function = resolved as? KtNamedFunction ?: return null
        if (!function.isPrivateTopLevelFunction()) {
            return null
        }
        if (function.containingKtFile != call.containingKtFile) {
            return null
        }
        return function
    }

    private fun collectReachablePrivateFunctions(root: KtNamedFunction): List<KtNamedFunction> {
        val result = linkedMapOf<String, KtNamedFunction>()
        val queue = ArrayDeque<KtNamedFunction>()
        fun enqueue(function: KtNamedFunction) {
            val key = function.textRange.startOffset.toString()
            if (result.putIfAbsent(key, function) == null) {
                queue.add(function)
            }
        }
        enqueue(root)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            current.collectDescendantsOfType<KtNameReferenceExpression>()
                .mapNotNull { reference -> resolvePrivateSameFileFunction(reference, root.containingKtFile) }
                .forEach(::enqueue)
        }
        return result.values.toList()
    }

    private fun resolvePrivateSameFileFunction(
        reference: KtNameReferenceExpression,
        file: KtFile,
    ): KtNamedFunction? {
        val resolved = runCatching { reference.mainReference.resolve() }.getOrNull()
        val function = resolved as? KtNamedFunction ?: return null
        if (function.containingKtFile != file || !function.isPrivateTopLevelFunction()) {
            return null
        }
        return function
    }

    private fun collectOrphanFunctions(
        file: KtFile,
        candidates: List<KtNamedFunction>,
        removedStatements: List<KtExpression>,
    ): List<KtNamedFunction> {
        val orphans = candidates.toMutableSet()
        var changed = true
        while (changed) {
            changed = false
            candidates.forEach { candidate ->
                if (candidate !in orphans) {
                    return@forEach
                }
                if (hasLiveReferences(file, candidate, orphans, removedStatements)) {
                    orphans.remove(candidate)
                    changed = true
                }
            }
        }
        return candidates.filter { function -> function in orphans }
    }

    private fun hasLiveReferences(
        file: KtFile,
        candidate: KtNamedFunction,
        scheduledForRemoval: Set<KtNamedFunction>,
        removedStatements: List<KtExpression>,
    ): Boolean {
        return ReferencesSearch.search(candidate, LocalSearchScope(file))
            .findAll()
            .any { reference ->
                val element = reference.element
                if (removedStatements.any { statement -> PsiTreeUtil.isAncestor(statement, element, false) }) {
                    return@any false
                }
                scheduledForRemoval.none { function ->
                    PsiTreeUtil.isAncestor(function, element, false)
                }
            }
    }

    private fun KtNamedFunction.isPrivateTopLevelFunction(): Boolean {
        return parent is KtFile && hasModifier(KtTokens.PRIVATE_KEYWORD)
    }
}

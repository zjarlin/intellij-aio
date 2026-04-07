package site.addzero.composebuddy.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.analysis.ComposeFunctionSupport
import site.addzero.composebuddy.refactor.ComposeRefactorEngine

class WrapLayoutContainerAsHigherOrderIntention : PsiElementBaseIntentionAction(), DumbAware {
    override fun getFamilyName(): String = ComposeBuddyBundle.message("plugin.name")

    override fun getText(): String = ComposeBuddyBundle.message("intention.wrap.higher.order.container")

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val currentEditor = editor ?: return false
        val ownerFunction = element.getStrictParentOfType<KtNamedFunction>() ?: return false
        if (!ComposeFunctionSupport.isComposable(ownerFunction)) return false
        val selectionRange = currentEditor.selectedRange() ?: return false
        val target = findSelectedContainer(ownerFunction, selectionRange, currentEditor.caretModel.offset) ?: return false
        return target.getStrictParentOfType<KtNamedFunction>() == ownerFunction
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val currentEditor = editor ?: return
        val ownerFunction = element.getStrictParentOfType<KtNamedFunction>() ?: return
        val selectionRange = currentEditor.selectedRange() ?: return
        val target = findSelectedContainer(ownerFunction, selectionRange, currentEditor.caretModel.offset) ?: return
        val functionName = generateUniqueContainerName(ownerFunction, target)
        ComposeRefactorEngine(project).wrapContainerAsHigherOrder(ownerFunction, target, functionName)
    }

    private fun findSelectedContainer(
        ownerFunction: KtNamedFunction,
        selectionRange: TextRange,
        caretOffset: Int,
    ): KtCallExpression? {
        val selectedCalls = ownerFunction.collectDescendantsOfType<KtCallExpression>()
            .filter { call -> call.isLayoutContainerCall() }
            .filter { call -> selectionRange.contains(call.textRange) }
        if (selectedCalls.isEmpty()) return null
        val containingCaret = selectedCalls.filter { call -> call.textRange.contains(caretOffset) }
        return (containingCaret.ifEmpty { selectedCalls })
            .maxByOrNull { call -> call.textRange.length }
    }

    private fun generateUniqueContainerName(ownerFunction: KtNamedFunction, call: KtCallExpression): String {
        val callee = call.calleeExpression?.text.orEmpty().ifBlank { "Container" }
        val base = "${callee}Container"
        val file = ownerFunction.containingKtFile
        val existingNames = file.collectDescendantsOfType<KtNamedFunction>()
            .mapNotNull { it.name }
            .toSet()
        if (base !in existingNames) return base
        var index = 2
        while (true) {
            val candidate = "$base$index"
            if (candidate !in existingNames) return candidate
            index++
        }
    }

    private fun KtCallExpression.isLayoutContainerCall(): Boolean {
        val name = calleeExpression?.text ?: return false
        return name in LAYOUT_CONTAINER_NAMES && lambdaArguments.isNotEmpty()
    }

    private fun Editor.selectedRange(): TextRange? {
        val model = selectionModel
        if (!model.hasSelection()) return null
        val start = model.selectionStart
        val end = model.selectionEnd
        if (start >= end) return null
        return TextRange(start, end)
    }

    private fun TextRange.contains(other: TextRange): Boolean {
        return other.startOffset >= startOffset && other.endOffset <= endOffset
    }

    companion object {
        private val LAYOUT_CONTAINER_NAMES = setOf(
            "Box",
            "Column",
            "Row",
            "LazyColumn",
            "LazyRow",
        )
    }
}

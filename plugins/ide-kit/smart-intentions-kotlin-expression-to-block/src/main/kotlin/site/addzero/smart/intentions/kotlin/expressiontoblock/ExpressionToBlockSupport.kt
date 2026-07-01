package site.addzero.smart.intentions.kotlin.expressiontoblock

import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

internal object ExpressionToBlockSupport {
    fun isApplicable(file: KtFile): Boolean {
        return file.collectDescendantsOfType<KtNamedFunction>().any(::isApplicable)
    }

    fun apply(file: KtFile): Int {
        val functions = file.collectDescendantsOfType<KtNamedFunction>()
            .filter(::isApplicable)
            .sortedByDescending { function -> function.textOffset }
        var convertedCount = 0
        functions.forEach { function ->
            if (function.isValid && isApplicable(function)) {
                convertFunction(function)
                convertedCount += 1
            }
        }
        return convertedCount
    }

    private fun isApplicable(fn: KtNamedFunction): Boolean {
        if (fn.bodyBlockExpression != null) return false
        if (fn.typeReference == null) return false
        fn.equalsToken ?: return false
        val bodyExpr = fn.bodyExpression ?: return false
        val originalBodyText = bodyExpr.text.trim()
        return originalBodyText.isNotBlank()
    }

    private fun convertFunction(fn: KtNamedFunction) {
        val eqToken = fn.equalsToken ?: return
        val bodyExpr = fn.bodyExpression ?: return
        val originalBodyText = bodyExpr.text.trim()

        val psiFactory = KtPsiFactory(fn.project)
        val isAlreadyReturn = bodyExpr is KtReturnExpression
        val indentedBody = originalBodyText.prependIndent("    ")
        val blockBodyText = if (isAlreadyReturn) {
            "{\n$indentedBody\n}"
        } else {
            "{\n    return $originalBodyText\n}"
        }

        // 通过移除 = bodyExpr 并插入块体来构建完整替换文本。
        val prefixLength = eqToken.node.startOffset - fn.node.startOffset
        val prefix = fn.text.substring(0, prefixLength)
        val suffix = fn.text.substring(bodyExpr.node.startOffset + bodyExpr.node.textLength - fn.node.startOffset)

        val newFnText = prefix + blockBodyText + suffix
        val newFn = psiFactory.createFunction(newFnText)
        val replaced = fn.replace(newFn) as KtNamedFunction
        CodeStyleManager.getInstance(fn.project).reformat(replaced)
    }
}

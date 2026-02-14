package site.addzero.nullfixer

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * 核心修复逻辑：扫描 Kotlin 文件中的空安全编译错误，
 * 自动修复。
 *
 * 支持的错误类型：
 * 1. "Only safe (?.) or non-null asserted (!!.) calls are allowed" - nullable receiver 不安全调用
 * 2. "Return type mismatch" - 返回类型不匹配（需要 Elvis 运算符）
 */
object NullSafetyFixer {

    private val UNSAFE_CALL_PATTERNS = listOf(
        "Only safe (?.) or non-null asserted (!!.) calls are allowed",
        "只允许安全(?.)或非空断言(!!.)调用",
    )

    private val RETURN_TYPE_MISMATCH_PATTERNS = listOf(
        "Return type mismatch",
        "返回类型不匹配",
    )

    data class FixResult(
        val fixed: Int,
        val skipped: Int,
        val fileName: String,
        val remainingErrors: Int = 0
    )

    fun fixFile(project: Project, psiFile: PsiFile): FixResult {
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
            ?: return FixResult(0, 0, psiFile.name)

        val unsafeCallRanges = collectUnsafeCallRanges(project, document)
        val returnMismatchRanges = collectReturnTypeMismatchRanges(project, document)

        val totalErrors = unsafeCallRanges.size + returnMismatchRanges.size
        if (totalErrors == 0) {
            return FixResult(0, 0, psiFile.name)
        }

        var fixed = 0
        var skipped = 0

        WriteCommandAction.runWriteCommandAction(project, "Fix Kotlin Null Safety", null, {
            for (range in unsafeCallRanges.sortedByDescending { it.startOffset }) {
                val dotExpr = findDotQualifiedExpression(psiFile, range)
                if (dotExpr != null && replaceDotWithSafeCall(project, dotExpr)) {
                    fixed++
                } else {
                    skipped++
                }
            }

            for (range in returnMismatchRanges.sortedByDescending { it.startOffset }) {
                if (addElvisOperator(project, psiFile, range)) {
                    fixed++
                } else {
                    skipped++
                }
            }
        }, psiFile)

        restartDaemonAnalysis(project, psiFile)

        val remainingErrors = countNullSafetyErrors(project, document)

        return FixResult(fixed, skipped, psiFile.name, remainingErrors)
    }

    private fun collectUnsafeCallRanges(project: Project, document: Document): List<TextRange> {
        val ranges = mutableListOf<TextRange>()
        DaemonCodeAnalyzerEx.processHighlights(
            document, project, HighlightSeverity.ERROR, 0, document.textLength
        ) { info: HighlightInfo ->
            val desc = info.description ?: ""
            if (UNSAFE_CALL_PATTERNS.any { desc.contains(it) }) {
                ranges.add(TextRange(info.startOffset, info.endOffset))
            }
            true
        }
        return ranges
    }

    private fun collectReturnTypeMismatchRanges(project: Project, document: Document): List<TextRange> {
        val ranges = mutableListOf<TextRange>()
        DaemonCodeAnalyzerEx.processHighlights(
            document, project, HighlightSeverity.ERROR, 0, document.textLength
        ) { info: HighlightInfo ->
            val desc = info.description ?: ""
            if (RETURN_TYPE_MISMATCH_PATTERNS.any { desc.contains(it) }) {
                ranges.add(TextRange(info.startOffset, info.endOffset))
            }
            true
        }
        return ranges
    }

    private fun countNullSafetyErrors(project: Project, document: Document): Int {
        var count = 0
        DaemonCodeAnalyzerEx.processHighlights(
            document, project, HighlightSeverity.ERROR, 0, document.textLength
        ) { info: HighlightInfo ->
            val desc = info.description ?: ""
            if (UNSAFE_CALL_PATTERNS.any { desc.contains(it) } ||
                RETURN_TYPE_MISMATCH_PATTERNS.any { desc.contains(it) }) {
                count++
            }
            true
        }
        return count
    }

    private fun restartDaemonAnalysis(project: Project, psiFile: PsiFile) {
        // 文件修改后 IDEA 会自动重新分析，无需手动触发
    }

    private fun findDotQualifiedExpression(psiFile: PsiFile, range: TextRange): KtDotQualifiedExpression? {
        val element = psiFile.findElementAt(range.startOffset) ?: return null
        return PsiTreeUtil.getParentOfType(element, KtDotQualifiedExpression::class.java)
    }

    private fun replaceDotWithSafeCall(project: Project, dotExpr: KtDotQualifiedExpression): Boolean {
        val receiverText = dotExpr.receiverExpression.text
        val selectorText = dotExpr.selectorExpression?.text ?: return false
        val factory = KtPsiFactory(project)
        val safeExpr = factory.createExpression("$receiverText?.$selectorText")
        dotExpr.replace(safeExpr)
        return true
    }

    private fun addElvisOperator(project: Project, psiFile: PsiFile, range: TextRange): Boolean {
        val element = psiFile.findElementAt(range.startOffset) ?: return false
        val parent = element.parent ?: return false

        val exprText = parent.text
        if (!exprText.endsWith("?")) {
            val factory = KtPsiFactory(project)
            val newExpr = factory.createExpression("$exprText ?: emptyList()")
            parent.replace(newExpr)
            return true
        }
        return false
    }
}

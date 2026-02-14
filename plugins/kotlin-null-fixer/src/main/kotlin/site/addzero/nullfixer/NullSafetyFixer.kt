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
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression

/**
 * 核心修复逻辑：
 * 1. UNSAFE_CALL → 将 `.` 替换为 `?.`
 * 2. RETURN_TYPE_MISMATCH (nullable → non-null) → 在表达式末尾加 `.orEmpty()` 或 `?: error("...")`
 */
object NullSafetyFixer {

    private val UNSAFE_CALL_PATTERNS = listOf(
        "Only safe (?.) or non-null asserted (!!.) calls are allowed",
        "只允许安全(?.)或非空断言(!!.)调用",
    )

    private val RETURN_MISMATCH_PATTERNS = listOf(
        "Return type mismatch",
        "返回类型不匹配",
    )

    /** 所有可修复的错误模式 */
    val ALL_PATTERNS: List<String> = UNSAFE_CALL_PATTERNS + RETURN_MISMATCH_PATTERNS

    data class FixResult(val fixed: Int, val skipped: Int, val fileName: String)

    private enum class ErrorKind { UNSAFE_CALL, RETURN_MISMATCH }

    private data class ErrorLocation(val range: TextRange, val kind: ErrorKind, val description: String)

    fun fixFile(project: Project, psiFile: PsiFile): FixResult {
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
            ?: return FixResult(0, 0, psiFile.name)

        val errors = collectErrors(project, document)
        if (errors.isEmpty()) return FixResult(0, 0, psiFile.name)

        var fixed = 0
        var skipped = 0

        WriteCommandAction.runWriteCommandAction(project, "Fix Kotlin Null Safety", null, {
            for (error in errors.sortedByDescending { it.range.startOffset }) {
                val success = when (error.kind) {
                    ErrorKind.UNSAFE_CALL -> fixUnsafeCall(project, psiFile, error.range)
                    ErrorKind.RETURN_MISMATCH -> fixReturnMismatch(project, psiFile, error)
                }
                if (success) fixed++ else skipped++
            }
        }, psiFile)

        return FixResult(fixed, skipped, psiFile.name)
    }

    /** 检查文件是否有可修复的错误 */
    fun hasFixableErrors(project: Project, document: Document): Boolean {
        var found = false
        DaemonCodeAnalyzerEx.processHighlights(
            document, project, HighlightSeverity.ERROR, 0, document.textLength
        ) { info: HighlightInfo ->
            val desc = info.description ?: ""
            if (ALL_PATTERNS.any { desc.contains(it) }) {
                found = true
            }
            !found
        }
        return found
    }

    private fun collectErrors(project: Project, document: Document): List<ErrorLocation> {
        val errors = mutableListOf<ErrorLocation>()
        DaemonCodeAnalyzerEx.processHighlights(
            document, project, HighlightSeverity.ERROR, 0, document.textLength
        ) { info: HighlightInfo ->
            val desc = info.description ?: ""
            val range = TextRange(info.startOffset, info.endOffset)
            when {
                UNSAFE_CALL_PATTERNS.any { desc.contains(it) } ->
                    errors.add(ErrorLocation(range, ErrorKind.UNSAFE_CALL, desc))
                RETURN_MISMATCH_PATTERNS.any { desc.contains(it) } ->
                    errors.add(ErrorLocation(range, ErrorKind.RETURN_MISMATCH, desc))
            }
            true
        }
        return errors
    }

    // ========== UNSAFE_CALL 修复 ==========

    private fun fixUnsafeCall(project: Project, psiFile: PsiFile, range: TextRange): Boolean {
        val element = psiFile.findElementAt(range.startOffset) ?: return false
        val dotExpr = PsiTreeUtil.getParentOfType(element, KtDotQualifiedExpression::class.java)
            ?: return false
        // 如果已经是安全调用就跳过
        if (dotExpr is KtSafeQualifiedExpression) return false
        val receiverText = dotExpr.receiverExpression.text
        val selectorText = dotExpr.selectorExpression?.text ?: return false
        val factory = KtPsiFactory(project)
        dotExpr.replace(factory.createExpression("$receiverText?.$selectorText"))
        return true
    }

    // ========== RETURN_TYPE_MISMATCH 修复 ==========

    /**
     * 修复返回类型不匹配：当函数返回 List<T> 但表达式是 List<T>? 时，
     * 在表达式末尾加 .orEmpty()（集合类型）或 ?: error("unexpected null")（其他类型）。
     *
     * 典型场景：
     *   return songs?.mapNotNull { ... }
     *   → return songs?.mapNotNull { ... }.orEmpty()
     *
     * 也处理隐式 return（函数体最后一个表达式）。
     */
    private fun fixReturnMismatch(project: Project, psiFile: PsiFile, error: ErrorLocation): Boolean {
        val element = psiFile.findElementAt(error.range.startOffset) ?: return false
        val desc = error.description
        val factory = KtPsiFactory(project)

        // 找到 return 表达式或者直接是表达式
        val returnExpr = PsiTreeUtil.getParentOfType(element, KtReturnExpression::class.java)
        val exprToFix = returnExpr?.returnedExpression
            ?: PsiTreeUtil.getParentOfType(element, KtSafeQualifiedExpression::class.java)
            ?: PsiTreeUtil.getParentOfType(element, KtDotQualifiedExpression::class.java)
            ?: return false

        val exprText = exprToFix.text

        // 判断期望的返回类型是否是集合类型
        val isCollectionReturn = COLLECTION_PATTERNS.any { desc.contains(it) }

        val fixedText = if (isCollectionReturn) {
            "$exprText.orEmpty()"
        } else {
            // 通用修复：加 elvis + 抛异常
            "($exprText ?: error(\"unexpected null\"))"
        }

        exprToFix.replace(factory.createExpression(fixedText))
        return true
    }

    /** 集合类型关键词，用于判断是否可以用 .orEmpty() */
    private val COLLECTION_PATTERNS = listOf(
        "List<", "Set<", "Map<", "Collection<",
        "MutableList<", "MutableSet<", "MutableMap<",
    )
}

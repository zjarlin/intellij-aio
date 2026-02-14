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

        // 先修 UNSAFE_CALL（. → ?.），再修 RETURN_MISMATCH，各自从后往前
        WriteCommandAction.runWriteCommandAction(project, "Fix Kotlin Null Safety", null, {
            val unsafeCalls = errors.filter { it.kind == ErrorKind.UNSAFE_CALL }
                .sortedByDescending { it.range.startOffset }
            val returnMismatches = errors.filter { it.kind == ErrorKind.RETURN_MISMATCH }
                .sortedByDescending { it.range.startOffset }

            for (error in unsafeCalls) {
                if (fixUnsafeCall(project, psiFile, error.range)) fixed++ else skipped++
            }
            PsiDocumentManager.getInstance(project).commitDocument(document)
            for (error in returnMismatches) {
                if (fixReturnMismatch(project, psiFile, error)) fixed++ else skipped++
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
     * 修复返回类型不匹配：当函数返回 List<T> 但表达式是 List<T>? 时。
     *
     * 策略：
     * - 集合类型 + 安全调用链 → 用 elvis `?: emptyList()` 而不是 `.orEmpty()`
     *   避免 `a?.b.orEmpty()` 产生新的 UNSAFE_CALL
     * - 集合类型 + 普通表达式 → `.orEmpty()`
     * - 非集合类型 → `?: error("unexpected null")`
     */
    private fun fixReturnMismatch(project: Project, psiFile: PsiFile, error: ErrorLocation): Boolean {
        val element = psiFile.findElementAt(error.range.startOffset) ?: return false
        val desc = error.description
        val factory = KtPsiFactory(project)

        val returnExpr = PsiTreeUtil.getParentOfType(element, KtReturnExpression::class.java)
        val exprToFix = returnExpr?.returnedExpression
            ?: PsiTreeUtil.getParentOfType(element, KtSafeQualifiedExpression::class.java)
            ?: PsiTreeUtil.getParentOfType(element, KtDotQualifiedExpression::class.java)
            ?: return false

        val exprText = exprToFix.text
        val isCollectionReturn = COLLECTION_PATTERNS.any { desc.contains(it) }
        val isSafeCallChain = exprToFix is KtSafeQualifiedExpression
            || PsiTreeUtil.findChildOfType(exprToFix, KtSafeQualifiedExpression::class.java) != null

        val fixedText = when {
            isCollectionReturn && isSafeCallChain ->
                // 用 elvis 避免 a?.b.orEmpty() 的 UNSAFE_CALL 问题
                "($exprText ?: emptyList())"
            isCollectionReturn ->
                "$exprText.orEmpty()"
            else ->
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

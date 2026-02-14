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
import org.jetbrains.kotlin.psi.*

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
            // === 第一步：修 UNSAFE_CALL（. → ?.） ===
            val unsafeCalls = errors.filter { it.kind == ErrorKind.UNSAFE_CALL }
                .sortedByDescending { it.range.startOffset }
            for (error in unsafeCalls) {
                if (fixUnsafeCall(project, psiFile, error.range)) fixed++ else skipped++
            }

            // === 第二步：修高亮缓存中已有的 RETURN_MISMATCH ===
            val returnMismatches = errors.filter { it.kind == ErrorKind.RETURN_MISMATCH }
                .sortedByDescending { it.range.startOffset }
            PsiDocumentManager.getInstance(project).commitDocument(document)
            for (error in returnMismatches) {
                if (fixReturnMismatch(project, psiFile, error)) fixed++ else skipped++
            }

            // === 第三步：PSI 主动扫描潜在的 return type mismatch ===
            // 修完 UNSAFE_CALL 后，可能产生新的 return type mismatch（高亮缓存里还没有）
            // 直接遍历 PSI 找：函数返回非空集合类型，但 return 表达式包含 ?. 安全调用链
            PsiDocumentManager.getInstance(project).commitDocument(document)
            val proactiveFixed = fixPotentialReturnMismatches(project, psiFile)
            fixed += proactiveFixed
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

        val fixedText = when {
            isCollectionReturn ->
                "$exprText.orEmpty()"
            else ->
                "($exprText ?: error(\"unexpected null\"))"
        }

        exprToFix.replace(factory.createExpression(fixedText))
        return true
    }

    /**
     * PSI 主动扫描：找到函数返回类型是非空集合但 return 表达式含 ?. 的情况，
     * 主动加 ?: emptyList() / emptySet() / emptyMap()。
     * 这样不依赖高亮缓存，修完 UNSAFE_CALL 后一步到位。
     */
    private fun fixPotentialReturnMismatches(project: Project, psiFile: PsiFile): Int {
        val factory = KtPsiFactory(project)
        var fixed = 0

        // 遍历所有函数声明
        val functions = PsiTreeUtil.findChildrenOfType(psiFile, KtNamedFunction::class.java)
        for (func in functions) {
            val returnTypeRef = func.typeReference?.text ?: continue
            // 判断返回类型是否是非空集合
            val emptyFactory = resolveEmptyFactory(returnTypeRef) ?: continue

            // 收集该函数内所有 return 表达式（从后往前处理）
            val returns = PsiTreeUtil.findChildrenOfType(func, KtReturnExpression::class.java)
                .sortedByDescending { it.textOffset }

            for (ret in returns) {
                val expr = ret.returnedExpression ?: continue
                // 只处理包含安全调用链的表达式
                if (expr !is KtSafeQualifiedExpression &&
                    PsiTreeUtil.findChildOfType(expr, KtSafeQualifiedExpression::class.java) == null) continue
                // 如果已经被 elvis 包裹了就跳过
                if (expr is KtBinaryExpression && expr.operationReference.text == "?:") continue
                if (expr.text.contains(".orEmpty()") || expr.text.contains("?: error(")) continue

                val fixedText = "${expr.text}.orEmpty()"
                expr.replace(factory.createExpression(fixedText))
                fixed++
            }

            // 也处理表达式体函数：fun foo(): List<T> = something?.bar()
            val bodyExpr = func.bodyExpression
            if (bodyExpr != null && func.hasBlockBody().not()) {
                if (bodyExpr is KtSafeQualifiedExpression ||
                    PsiTreeUtil.findChildOfType(bodyExpr, KtSafeQualifiedExpression::class.java) != null) {
                    if (!bodyExpr.text.contains(".orEmpty()") && !bodyExpr.text.contains("?: error(")) {
                        val fixedText = "${bodyExpr.text}.orEmpty()"
                        bodyExpr.replace(factory.createExpression(fixedText))
                        fixed++
                    }
                }
            }
        }
        return fixed
    }

    /** 根据返回类型文本判断用哪个 empty 工厂方法，非集合/非String返回 null */
    private fun resolveEmptyFactory(returnType: String): String? {
        val trimmed = returnType.trimStart()
        return when {
            trimmed.startsWith("List<") || trimmed.startsWith("MutableList<") -> "emptyList()"
            trimmed.startsWith("Set<") || trimmed.startsWith("MutableSet<") -> "emptySet()"
            trimmed.startsWith("Map<") || trimmed.startsWith("MutableMap<") -> "emptyMap()"
            trimmed.startsWith("Collection<") -> "emptyList()"
            trimmed == "String" -> "\"\""
            else -> null
        }
    }

    /** 集合类型关键词，用于判断是否可以用 .orEmpty() */
    private val COLLECTION_PATTERNS = listOf(
        "List<", "Set<", "Map<", "Collection<",
        "MutableList<", "MutableSet<", "MutableMap<",
    )
}

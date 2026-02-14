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
 * 核心修复逻辑：扫描 Kotlin 文件中的 UNSAFE_CALL 编译错误，
 * 将 `.` 调用替换为 `?.` 安全调用。
 *
 * 原理：
 * 1. 从 DaemonCodeAnalyzer 获取当前文件的所有高亮信息（编译错误/警告）
 * 2. 筛选出 "Only safe (?.) or non-null asserted (!!.)" 错误
 * 3. 定位到对应的 KtDotQualifiedExpression PSI 节点
 * 4. 用 KtPsiFactory 将 receiver.selector 替换为 receiver?.selector
 */
object NullSafetyFixer {

    /** 匹配 UNSAFE_CALL 错误的关键词（中英文） */
    private val UNSAFE_CALL_PATTERNS = listOf(
        "Only safe (?.) or non-null asserted (!!.) calls are allowed",
        "只允许安全(?.)或非空断言(!!.)调用",
    )

    data class FixResult(val fixed: Int, val skipped: Int, val fileName: String)

    /**
     * 修复单个文件中的所有 nullable receiver 不安全调用
     */
    fun fixFile(project: Project, psiFile: PsiFile): FixResult {
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
            ?: return FixResult(0, 0, psiFile.name)

        val unsafeCallRanges = collectUnsafeCallRanges(project, document)
        if (unsafeCallRanges.isEmpty()) {
            return FixResult(0, 0, psiFile.name)
        }

        var fixed = 0
        var skipped = 0

        WriteCommandAction.runWriteCommandAction(project, "Fix Kotlin Null Safety", null, {
            // 从后往前替换，避免偏移量变化影响前面的位置
            for (range in unsafeCallRanges.sortedByDescending { it.startOffset }) {
                val dotExpr = findDotQualifiedExpression(psiFile, range)
                if (dotExpr != null && replaceDotWithSafeCall(project, dotExpr)) {
                    fixed++
                } else {
                    skipped++
                }
            }
        }, psiFile)

        return FixResult(fixed, skipped, psiFile.name)
    }

    /** 从 DaemonCodeAnalyzer 收集 UNSAFE_CALL 错误位置 */
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

    /**
     * 在错误高亮范围内找到 KtDotQualifiedExpression。
     *
     * 编译器错误通常标记在 selector（方法名/属性名）上，
     * 需要向上查找包含它的 KtDotQualifiedExpression。
     */
    private fun findDotQualifiedExpression(psiFile: PsiFile, range: TextRange): KtDotQualifiedExpression? {
        val element = psiFile.findElementAt(range.startOffset) ?: return null
        return PsiTreeUtil.getParentOfType(element, KtDotQualifiedExpression::class.java)
    }

    /**
     * 将 `receiver.selector` 替换为 `receiver?.selector`。
     *
     * 只替换最内层的 `.`，不会影响链式调用的其他部分。
     * 例如 `a.b.c` 中如果 `a` 是 nullable，只替换 `a.b` → `a?.b`。
     *
     * @return true 如果替换成功
     */
    private fun replaceDotWithSafeCall(project: Project, dotExpr: KtDotQualifiedExpression): Boolean {
        val receiverText = dotExpr.receiverExpression.text
        val selectorText = dotExpr.selectorExpression?.text ?: return false
        val factory = KtPsiFactory(project)
        val safeExpr = factory.createExpression("$receiverText?.$selectorText")
        dotExpr.replace(safeExpr)
        return true
    }
}

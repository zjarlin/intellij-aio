package site.addzero.java.nullfixer

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile

/**
 * 核心修复逻辑：扫描 Java 文件中的空指针相关警告/错误，
 * 批量调用 IntelliJ 内置的 Quick Fix 来修复。
 *
 * 原理：
 * 1. 从 DaemonCodeAnalyzer 获取文件的所有高亮信息
 * 2. 筛选出空指针相关的警告（NullPointerException / @Nullable / null check 等）
 * 3. 收集每个高亮上附带的 QuickFix / IntentionAction
 * 4. 优先选择 "Surround with null check" 类型的 fix，批量执行
 */
object JavaNullSafetyFixer {

    /**
     * 匹配空指针相关警告的关键词。
     * 覆盖 IntelliJ 内置检查 + 各注解框架（JSpecify、JetBrains、JSR-305、
     * Checker Framework、Android、Lombok、Eclipse 等）产生的警告。
     * 只匹配真正需要 null check 修复的场景，避免误判。
     */
    val NULL_WARNING_PATTERNS = listOf(
        // IntelliJ 内置 NullPointerException 检查
        "NullPointerException",
        "may produce",
        // 解引用 null
        "Dereference of",
        // 传参 null
        "passing 'null'",
        "passing null",
        // 方法返回值可能为 null
        "might be null",
        "could be null",
        "may be null",
        // 注解驱动的警告（不限定包名，短名称匹配）
        "@Nullable",
        "@NonNull",
        "@NotNull",
        "@Nonnull",
        "@CheckForNull",
        // 自动拆箱可能 NPE
        "unboxing of",
    )

    /** Quick Fix 名称优先级（优先选择 surround with null check） */
    private val FIX_PRIORITY = listOf(
        "Surround with null check",
        "surround with null check",
        "Add null check",
        "Replace with",
        "Insert null check",
        "Wrap with Objects.requireNonNull",
    )

    data class FixResult(val fixed: Int, val skipped: Int, val fileName: String)

    /**
     * 检查文件是否有可修复的空指针警告
     */
    fun hasFixableErrors(project: Project, document: Document): Boolean {
        var found = false
        DaemonCodeAnalyzerEx.processHighlights(
            document, project, HighlightSeverity.WARNING, 0, document.textLength
        ) { info: HighlightInfo ->
            val desc = info.description ?: ""
            if (isNullWarning(desc) && hasApplicableFix(info)) {
                found = true
            }
            !found
        }
        if (!found) {
            // 也检查 ERROR 级别
            DaemonCodeAnalyzerEx.processHighlights(
                document, project, HighlightSeverity.ERROR, 0, document.textLength
            ) { info: HighlightInfo ->
                val desc = info.description ?: ""
                if (isNullWarning(desc) && hasApplicableFix(info)) {
                    found = true
                }
                !found
            }
        }
        return found
    }

    /**
     * 批量修复单个文件中的所有空指针警告
     */
    fun fixFile(project: Project, psiFile: PsiFile, editor: Editor): FixResult {
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
            ?: return FixResult(0, 0, psiFile.name)

        // 收集所有可修复的空指针问题及其 quick fix
        data class FixCandidate(val offset: Int, val fix: IntentionAction, val desc: String)

        val candidates = mutableListOf<FixCandidate>()

        for (severity in listOf(HighlightSeverity.WARNING, HighlightSeverity.ERROR)) {
            DaemonCodeAnalyzerEx.processHighlights(
                document, project, severity, 0, document.textLength
            ) { info: HighlightInfo ->
                val desc = info.description ?: ""
                if (isNullWarning(desc)) {
                    val fix = findBestFix(info, psiFile, project, editor)
                    if (fix != null) {
                        candidates.add(FixCandidate(info.startOffset, fix, desc))
                    }
                }
                true
            }
        }

        if (candidates.isEmpty()) return FixResult(0, 0, psiFile.name)

        var fixed = 0
        var skipped = 0

        // 从后往前修复，避免偏移量变化
        val sorted = candidates.sortedByDescending { it.offset }

        WriteCommandAction.runWriteCommandAction(project, "Fix Java Null Safety", null, {
            for (candidate in sorted) {
                try {
                    candidate.fix.invoke(project, editor, psiFile)
                    PsiDocumentManager.getInstance(project).commitDocument(document)
                    fixed++
                } catch (_: Exception) {
                    skipped++
                }
            }
        }, psiFile)

        return FixResult(fixed, skipped, psiFile.name)
    }

    private fun isNullWarning(desc: String): Boolean {
        return NULL_WARNING_PATTERNS.any { pattern ->
            desc.contains(pattern, ignoreCase = true)
        }
    }

    private fun hasApplicableFix(info: HighlightInfo): Boolean {
        val fixes = mutableListOf<IntentionAction>()
        info.findRegisteredQuickFix<Any?> { descriptor, _ ->
            descriptor.action?.let { fixes.add(it) }
            null
        }
        return fixes.isNotEmpty()
    }

    /**
     * 从高亮信息中找到最合适的 quick fix。
     * 优先选择 "Surround with null check" 类型。
     */
    private fun findBestFix(
        info: HighlightInfo,
        psiFile: PsiFile,
        project: Project,
        editor: Editor
    ): IntentionAction? {
        val fixes = mutableListOf<IntentionAction>()
        info.findRegisteredQuickFix<Any?> { descriptor, _ ->
            descriptor.action?.let { fixes.add(it) }
            null
        }

        if (fixes.isEmpty()) return null

        // 按优先级排序
        for (preferred in FIX_PRIORITY) {
            val match = fixes.find { fix ->
                fix.text.contains(preferred, ignoreCase = true) &&
                fix.isAvailable(project, editor, psiFile)
            }
            if (match != null) return match
        }

        // 没有匹配优先级的，返回第一个可用的
        return fixes.firstOrNull { it.isAvailable(project, editor, psiFile) }
    }
}

package site.addzero.diagnostic.core

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import site.addzero.diagnostic.model.DiagnosticItem
import site.addzero.diagnostic.model.DiagnosticSeverity
import site.addzero.diagnostic.model.FileDiagnostics

/**
 * 核心扩展函数：收集单个文件的诊断信息
 * 这是最基础的诊断收集能力，不依赖任何缓存或批量处理逻辑
 *
 * @param project 当前项目
 * @return 文件的诊断信息，如果没有问题则返回 null
 */
fun VirtualFile.collectDiagnostics(project: Project): FileDiagnostics? {
    if (!this.isValid || this.isDirectory) return null

    val psiManager = PsiManager.getInstance(project)
    val psiFile = psiManager.findFile(this) ?: return null

    val documentManager = PsiDocumentManager.getInstance(project)
    val document = documentManager.getDocument(psiFile)
    if (document != null && documentManager.isUncommited(document)) {
        documentManager.commitDocument(document)
    }

    val items = mutableListOf<DiagnosticItem>()

    // 1. 语法错误（PsiErrorElement）
    psiFile.checkSyntaxErrors(this, items)

    // 2. 文档高亮（DocumentMarkupModel）
    psiFile.checkDocumentHighlights(project, items)

    // 3. 已打开文件的文件级高亮（Daemon）
    psiFile.checkDaemonHighlights(project, items)

    // 4. Wolf 兜底
    checkWolfProblems(project, psiFile, items)

    return if (items.isNotEmpty()) {
        FileDiagnostics(this, psiFile, items.distinctBy { it.lineNumber to it.message })
    } else {
        null
    }
}

/**
 * 检查语法错误 - 通过遍历 PSI 树找到 PsiErrorElement
 */
private fun PsiFile.checkSyntaxErrors(file: VirtualFile, items: MutableList<DiagnosticItem>) {
    if (textLength == 0) return

    PsiTreeUtil.findChildrenOfType(this, PsiErrorElement::class.java).forEach { errorElement ->
        val lineNumber = try {
            val document = PsiDocumentManager.getInstance(project).getDocument(this)
            document?.getLineNumber(errorElement.textOffset)?.plus(1) ?: 1
        } catch (_: Exception) {
            1
        }

        val errorText = errorElement.errorDescription
        if (items.none { it.lineNumber == lineNumber && it.message == errorText }) {
            items.add(
                DiagnosticItem(
                    file = file,
                    psiFile = this,
                    lineNumber = lineNumber,
                    message = errorText,
                    severity = DiagnosticSeverity.ERROR
                )
            )
        }
    }
}

/**
 * 检查 DocumentMarkupModel 的高亮（基于 IDE 已产出的真实结果）
 */
private fun PsiFile.checkDocumentHighlights(project: Project, items: MutableList<DiagnosticItem>) {
    val file = virtualFile ?: return
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return

    try {
        val markupModel = DocumentMarkupModel.forDocument(document, project, false) ?: return
        markupModel.allHighlighters.forEach { highlighter ->
            val info = HighlightInfo.fromRangeHighlighter(highlighter) ?: return@forEach
            if (info.severity < HighlightSeverity.WARNING) return@forEach

            val message = info.description ?: return@forEach
            val lineNumber = try {
                document.getLineNumber(info.startOffset) + 1
            } catch (_: Exception) {
                return@forEach
            }

            if (items.none { it.lineNumber == lineNumber && it.message == message }) {
                items.add(
                    DiagnosticItem(
                        file = file,
                        psiFile = this,
                        lineNumber = lineNumber,
                        message = message,
                        severity = if (info.severity >= HighlightSeverity.ERROR) {
                            DiagnosticSeverity.ERROR
                        } else {
                            DiagnosticSeverity.WARNING
                        }
                    )
                )
            }
        }
    } catch (_: Exception) {
        // 静默处理
    }
}

/**
 * 检查 WolfTheProblemSolver 标记的问题
 */
private fun VirtualFile.checkWolfProblems(project: Project, psiFile: PsiFile, items: MutableList<DiagnosticItem>) {
    val wolf = WolfTheProblemSolver.getInstance(project)
    if (!wolf.isProblemFile(this)) return
    if (items.isNotEmpty()) return

    val errorInfo = analyzeCompilationError(psiFile)
    items.add(
        DiagnosticItem(
            file = this,
            psiFile = psiFile,
            lineNumber = errorInfo?.first ?: 1,
            message = errorInfo?.second ?: "编译错误（请在编辑器中查看详情）",
            severity = DiagnosticSeverity.ERROR
        )
    )
}

/**
 * 分析编译错误，尝试从 PSI 结构中提取一些有用的信息
 */
private fun analyzeCompilationError(psiFile: PsiFile): Pair<Int, String>? {
    val firstErrorChild = PsiTreeUtil.findChildOfType(psiFile, PsiErrorElement::class.java)
    if (firstErrorChild != null) {
        val line = try {
            val document = PsiDocumentManager.getInstance(psiFile.project).getDocument(psiFile)
            document?.getLineNumber(firstErrorChild.textOffset)?.plus(1) ?: 1
        } catch (_: Exception) {
            1
        }
        return line to firstErrorChild.errorDescription
    }
    return null
}

/**
 * 检查 DaemonCodeAnalyzer 的文件级高亮信息（仅已打开文件）
 */
private fun PsiFile.checkDaemonHighlights(project: Project, items: MutableList<DiagnosticItem>) {
    val file = virtualFile ?: return

    try {
        val openFiles = FileEditorManager.getInstance(project).openFiles
        if (file !in openFiles) return

        val daemonImpl = DaemonCodeAnalyzer.getInstance(project) as? DaemonCodeAnalyzerImpl ?: return
        val document = PsiDocumentManager.getInstance(project).getDocument(this) ?: return

        val method = DaemonCodeAnalyzerImpl::class.java.getDeclaredMethod(
            "getFileLevelHighlights",
            Project::class.java,
            PsiFile::class.java
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val highlights = method.invoke(daemonImpl, project, this) as? List<HighlightInfo>

        highlights?.forEach { info ->
            if (info.severity < HighlightSeverity.WARNING) return@forEach

            val message = info.description ?: return@forEach
            val lineNumber = try {
                document.getLineNumber(info.startOffset) + 1
            } catch (_: Exception) {
                return@forEach
            }

            if (items.none { it.lineNumber == lineNumber && it.message == message }) {
                items.add(
                    DiagnosticItem(
                        file = file,
                        psiFile = this,
                        lineNumber = lineNumber,
                        message = message,
                        severity = if (info.severity >= HighlightSeverity.ERROR) {
                            DiagnosticSeverity.ERROR
                        } else {
                            DiagnosticSeverity.WARNING
                        }
                    )
                )
            }
        }
    } catch (_: Exception) {
        // 静默处理
    }
}

/**
 * 批量收集多个文件的诊断信息
 * 这是对单文件收集的简单批量封装
 *
 * @param project 当前项目
 * @param files 要扫描的文件列表
 * @param onProgress 进度回调 (当前索引, 总数, 当前文件)
 * @return 所有文件的诊断信息列表
 */
fun List<VirtualFile>.collectDiagnostics(
    project: Project,
    onProgress: ((Int, Int, VirtualFile) -> Unit)? = null
): List<FileDiagnostics> {
    return mapIndexedNotNull { index, file ->
        onProgress?.invoke(index, size, file)
        file.collectDiagnostics(project)
    }
}

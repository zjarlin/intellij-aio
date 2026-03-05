package site.addzero.diagnostic.core

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
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

    val items = mutableListOf<DiagnosticItem>()

    // 1. 检查语法错误（红色波浪线）- 通过遍历 PSI 树找到 PsiErrorElement
    psiFile.checkSyntaxErrors(this, items)

    // 2. 检查 WolfTheProblemSolver 标记的编译问题
    checkWolfProblems(project, psiFile, items)

    // 3. 对于已打开的文件，检查 DaemonCodeAnalyzer 的高亮信息
    psiFile.checkDaemonHighlights(project, items)

    // 4. 深度分析：如果文件有问题但没获取到详细信息，运行 Inspection
    if (items.isNotEmpty() && items.all { it.message.contains("编译错误") || it.message.contains("请在编辑器") }) {
        psiFile.runDeepInspection(this, items)
    }

    return if (items.isNotEmpty()) {
        FileDiagnostics(this, psiFile, items.distinctBy { it.lineNumber to it.message })
    } else null
}

/**
 * 检查语法错误 - 通过遍历 PSI 树找到 PsiErrorElement
 */
private fun PsiFile.checkSyntaxErrors(file: VirtualFile, items: MutableList<DiagnosticItem>) {
    PsiTreeUtil.findChildrenOfType(this, PsiErrorElement::class.java).forEach { errorElement ->
        val lineNumber = try {
            val document = PsiDocumentManager.getInstance(project).getDocument(this)
            document?.getLineNumber(errorElement.textOffset)?.plus(1) ?: 1
        } catch (_: Exception) {
            1
        }

        val errorText = errorElement.errorDescription

        // 避免重复添加相同的错误
        if (items.none { it.lineNumber == lineNumber && it.message == errorText }) {
            items.add(DiagnosticItem(
                file = file,
                psiFile = this,
                lineNumber = lineNumber,
                message = errorText,
                severity = DiagnosticSeverity.ERROR
            ))
        }
    }
}

/**
 * 检查 WolfTheProblemSolver 标记的问题
 * WolfTheProblemSolver 标记的是编译错误（红色波浪线）
 */
private fun VirtualFile.checkWolfProblems(project: Project, psiFile: PsiFile, items: MutableList<DiagnosticItem>) {
    val wolf = WolfTheProblemSolver.getInstance(project)
    if (!wolf.isProblemFile(this)) return

    // 如果 PsiErrorElement 或 DaemonCodeAnalyzer 已经捕获了错误，就不再添加
    if (items.isNotEmpty()) return

    // WolfTheProblemSolver 只告诉我们文件有问题，没有提供详细的错误信息 API
    // 尝试通过 PsiFile 的 text 分析来获取一些信息
    val errorInfo = analyzeCompilationError(psiFile)

    items.add(DiagnosticItem(
        file = this,
        psiFile = psiFile,
        lineNumber = errorInfo?.first ?: 1,
        message = errorInfo?.second ?: "编译错误（请在编辑器中查看详情）",
        severity = DiagnosticSeverity.ERROR
    ))
}

/**
 * 分析编译错误，尝试从 PSI 结构中提取一些有用的信息
 */
private fun analyzeCompilationError(psiFile: PsiFile): Pair<Int, String>? {
    // 尝试找到第一个看起来有问题的地方
    val firstErrorChild = PsiTreeUtil.findChildOfType(psiFile, PsiErrorElement::class.java)
    if (firstErrorChild != null) {
        val line = try {
            val document = PsiDocumentManager.getInstance(psiFile.project).getDocument(psiFile)
            document?.getLineNumber(firstErrorChild.textOffset)?.plus(1) ?: 1
        } catch (_: Exception) { 1 }
        return line to firstErrorChild.errorDescription
    }

    // 如果 PSI 没有错误元素，可能是语义错误（如类型不匹配）
    // 这种情况下只能靠 DaemonCodeAnalyzer 的高亮信息
    return null
}

/**
 * 检查 DaemonCodeAnalyzer 的高亮信息
 */
private fun PsiFile.checkDaemonHighlights(project: Project, items: MutableList<DiagnosticItem>) {
    val file = this.virtualFile ?: return

    try {
        // 只有文件在编辑器中打开时才检查 DaemonCodeAnalyzer 高亮
        val openFiles = FileEditorManager.getInstance(project).openFiles
        if (file !in openFiles) return

        val daemonImpl = DaemonCodeAnalyzer.getInstance(project) as? DaemonCodeAnalyzerImpl ?: return
        val document = PsiDocumentManager.getInstance(project).getDocument(this) ?: return

        // 使用反射获取文件级别的高亮
        val method = DaemonCodeAnalyzerImpl::class.java.getDeclaredMethod(
            "getFileLevelHighlights",
            Project::class.java,
            PsiFile::class.java
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val highlights = method.invoke(daemonImpl, project, this) as? List<HighlightInfo>

        highlights?.forEach { info ->
            if (info.severity >= HighlightSeverity.ERROR || info.severity >= HighlightSeverity.WARNING) {
                val message = info.description ?: return@forEach
                val lineNumber = try {
                    document.getLineNumber(info.startOffset) + 1
                } catch (_: Exception) {
                    return@forEach
                }

                // 避免重复
                if (items.none { it.lineNumber == lineNumber && it.message == message }) {
                    items.add(DiagnosticItem(
                        file = file,
                        psiFile = this,
                        lineNumber = lineNumber,
                        message = message,
                        severity = if (info.severity >= HighlightSeverity.ERROR)
                            DiagnosticSeverity.ERROR else DiagnosticSeverity.WARNING
                    ))
                }
            }
        }
    } catch (_: Exception) {
        // 静默处理
    }
}

/**
 * 深度分析 - 使用 Inspection 获取未打开文件的具体错误
 * 这会运行所有启用的本地检查，速度较慢但信息完整
 */
private fun PsiFile.runDeepInspection(file: VirtualFile, items: MutableList<DiagnosticItem>) {
    try {
        val inspectionManager = InspectionManager.getInstance(project)
        val profile = InspectionProjectProfileManager.getInstance(project).currentProfile

        // 获取所有启用的本地检查工具
        val tools = profile.getAllEnabledInspectionTools(project)
            .filter { it.tool.tool is com.intellij.codeInspection.LocalInspectionTool }

        val newItems = mutableListOf<DiagnosticItem>()

        tools.forEach { toolState ->
            try {
                val tool = toolState.tool.tool as com.intellij.codeInspection.LocalInspectionTool
                val holder = com.intellij.codeInspection.ProblemsHolder(inspectionManager, this, false)

                val visitor = tool.buildVisitor(holder, false)
                this.accept(visitor)

                holder.results.forEach { descriptor ->
                    val lineNumber = descriptor.lineNumber + 1
                    val severity = when (descriptor.highlightType) {
                        ProblemHighlightType.ERROR,
                        ProblemHighlightType.GENERIC_ERROR -> DiagnosticSeverity.ERROR
                        else -> DiagnosticSeverity.WARNING
                    }

                    // 避免重复
                    if (items.none { it.lineNumber == lineNumber && it.message == descriptor.descriptionTemplate }) {
                        newItems.add(DiagnosticItem(
                            file = file,
                            psiFile = this,
                            lineNumber = lineNumber,
                            message = descriptor.descriptionTemplate,
                            severity = severity
                        ))
                    }
                }
            } catch (_: Exception) {
                // 单个检查失败继续下一个
            }
        }

        // 如果用 Inspection 获取到了具体信息，替换掉模糊的通用错误
        if (newItems.isNotEmpty()) {
            items.removeAll { it.message.contains("编译错误") || it.message.contains("请在编辑器") }
            items.addAll(newItems)
        }
    } catch (_: Exception) {
        // 深度分析失败，保留原有信息
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
    return this.mapIndexedNotNull { index, file ->
        onProgress?.invoke(index, this.size, file)
        file.collectDiagnostics(project)
    }
}

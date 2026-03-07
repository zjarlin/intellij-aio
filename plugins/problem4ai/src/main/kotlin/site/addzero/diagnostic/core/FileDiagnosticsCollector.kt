package site.addzero.diagnostic.core

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInspection.InspectionEngine
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.diagnostic.PluginException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import site.addzero.diagnostic.model.DiagnosticItem
import site.addzero.diagnostic.model.DiagnosticSeverity
import site.addzero.diagnostic.model.FileDiagnostics
import java.util.concurrent.atomic.AtomicBoolean

private val LOG: Logger = Logger.getInstance("site.addzero.diagnostic.core.FileDiagnosticsCollector")
private val OFFLINE_FALLBACK_DISABLED = AtomicBoolean(false)
private val OFFLINE_FALLBACK_NOTICE_LOGGED = AtomicBoolean(false)
private val ENABLE_OFFLINE_INSPECTION_FALLBACK: Boolean =
    java.lang.Boolean.getBoolean("problem4ai.enable.offline.fallback")

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
    val filePath = this.path

    // 1. 语法错误（PsiErrorElement）
    val syntaxBefore = items.size
    psiFile.checkSyntaxErrors(this, items)
    val syntaxAdded = items.size - syntaxBefore

    // 2. 文档高亮（DocumentMarkupModel）
    val documentBefore = items.size
    psiFile.checkDocumentHighlights(project, items)
    val documentAdded = items.size - documentBefore

    // 3. 未打开文件执行 Daemon 主高亮，拿到与编辑器一致的细节（无需打开编辑器）
    val isFileOpen = FileEditorManager.getInstance(project).openFiles.contains(this)
    var daemonAdded = 0
    var fallbackAdded = 0
    if (!isFileOpen) {
        val beforeDaemonCount = items.size
        psiFile.runDaemonMainPasses(project, items)
        daemonAdded = items.size - beforeDaemonCount
        if (items.size == beforeDaemonCount && ENABLE_OFFLINE_INSPECTION_FALLBACK) {
            val beforeFallbackCount = items.size
            psiFile.runOfflineInspectionFallback(this, items)
            fallbackAdded = items.size - beforeFallbackCount
        } else if (items.size == beforeDaemonCount &&
            OFFLINE_FALLBACK_NOTICE_LOGGED.compareAndSet(false, true)
        ) {
            LOG.info(
                "[Problem4AI][Collect] offline inspection fallback is disabled by default; " +
                        "enable with -Dproblem4ai.enable.offline.fallback=true"
            )
        }
    }

    // 4. 已打开文件的文件级高亮（Daemon）
    val openFileDaemonBefore = items.size
    psiFile.checkDaemonHighlights(project, items)
    val openFileDaemonAdded = items.size - openFileDaemonBefore

    // 5. Wolf 兜底
    val wolfBefore = items.size
    checkWolfProblems(project, psiFile, items)
    val wolfAdded = items.size - wolfBefore

    LOG.debug(
        "[Problem4AI][Collect] file=$filePath open=$isFileOpen syntax=$syntaxAdded doc=$documentAdded " +
                "daemon=$daemonAdded fallback=$fallbackAdded fileLevel=$openFileDaemonAdded wolf=$wolfAdded total=${items.size}"
    )

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
 * 对未打开文件执行 Daemon 主高亮，收集细粒度问题详情（含解析/类型错误）
 */
private fun PsiFile.runDaemonMainPasses(project: Project, items: MutableList<DiagnosticItem>) {
    collectDaemonHighlightsFromMarkup(project, items, source = "cached")
}

private fun PsiFile.collectDaemonHighlightsFromMarkup(
    project: Project,
    items: MutableList<DiagnosticItem>,
    source: String
) {
    val file = virtualFile ?: return
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return

    try {
        val highlights = mutableListOf<HighlightInfo>()
        DaemonCodeAnalyzerEx.processHighlights(
            document,
            project,
            HighlightSeverity.WARNING,
            0,
            document.textLength
        ) { info ->
            highlights.add(info)
            true
        }

        var accepted = 0
        highlights.forEach { info ->
            if (info.severity < HighlightSeverity.WARNING) return@forEach

            val message = info.description ?: return@forEach
            val lineNumber = try {
                document.getLineNumber(info.startOffset.coerceAtLeast(0)) + 1
            } catch (_: Exception) {
                1
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
                accepted++
            }
        }

        LOG.debug("[Problem4AI][Collect] daemon-$source highlights=${highlights.size} accepted=$accepted file=${file.path}")
    } catch (_: ProcessCanceledException) {
        // Cancellation is expected and should not be logged.
    } catch (e: Exception) {
        LOG.debug("daemon-$source highlight collect failed for file: ${file.path}", e)
    }
}

/**
 * Daemon 未产出结果时，使用语言感知的 InspectionEngine 作为兜底。
 */
private fun PsiFile.runOfflineInspectionFallback(file: VirtualFile, items: MutableList<DiagnosticItem>) {
    if (OFFLINE_FALLBACK_DISABLED.get()) {
        return
    }

    if (DumbService.getInstance(project).isDumb) {
        LOG.debug("[Problem4AI][Collect] skip offline fallback in dumb mode file=${file.path}")
        return
    }

    try {
        val profile = InspectionProjectProfileManager.getInstance(project).currentProfile
        val document = PsiDocumentManager.getInstance(project).getDocument(this)

        val tools = profile.getInspectionTools(this).mapNotNull { it as? LocalInspectionToolWrapper }
        if (tools.isEmpty()) {
            return
        }

        val result = InspectionEngine.inspectElements(
            tools,
            this,
            textRange,
            false,
            false,
            EmptyProgressIndicator(),
            listOf(this)
        ) { _, _ -> true }
        var acceptedCount = 0

        result.values.flatten().forEach { descriptor ->
            val message = descriptor.descriptionTemplate
            if (message.isBlank()) {
                return@forEach
            }

            val lineNumber = when {
                descriptor.lineNumber > 0 -> descriptor.lineNumber
                document != null -> {
                    val offset = descriptor.psiElement?.textOffset
                        ?: descriptor.startElement?.textOffset
                        ?: 0
                    document.getLineNumber(offset.coerceAtLeast(0)) + 1
                }
                else -> 1
            }

            val severity = when (descriptor.highlightType) {
                ProblemHighlightType.ERROR,
                ProblemHighlightType.GENERIC_ERROR,
                ProblemHighlightType.LIKE_UNKNOWN_SYMBOL -> DiagnosticSeverity.ERROR
                else -> DiagnosticSeverity.WARNING
            }

            if (items.none { it.lineNumber == lineNumber && it.message == message }) {
                items.add(
                    DiagnosticItem(
                        file = file,
                        psiFile = this,
                        lineNumber = lineNumber,
                        message = message,
                        severity = severity
                    )
                )
                acceptedCount++
            }
        }
        LOG.debug(
            "[Problem4AI][Collect] offline fallback tools=${tools.size} accepted=$acceptedCount file=${file.path}"
        )
    } catch (_: IndexNotReadyException) {
        LOG.debug("[Problem4AI][Collect] skip offline fallback index-not-ready file=${file.path}")
    } catch (_: ProcessCanceledException) {
        // Cancellation is expected and should not be logged.
    } catch (e: PluginException) {
        if (e.hasCause(IndexNotReadyException::class.java)) {
            LOG.debug("[Problem4AI][Collect] skip offline fallback plugin/index-not-ready file=${file.path}")
            return
        }
        if (OFFLINE_FALLBACK_DISABLED.compareAndSet(false, true)) {
            LOG.warn(
                "[Problem4AI][Collect] disable offline fallback due plugin exception: ${e.message}"
            )
        }
    } catch (e: Exception) {
        if (e.hasCause(IndexNotReadyException::class.java)) {
            LOG.debug("[Problem4AI][Collect] skip offline fallback nested index-not-ready file=${file.path}")
            return
        }
        LOG.debug("offline inspection fallback failed for file: ${file.path}", e)
    }
}

private fun Throwable.hasCause(clazz: Class<out Throwable>): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (clazz.isInstance(current)) {
            return true
        }
        current = current.cause
    }
    return false
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

        collectDaemonHighlightsFromMarkup(project, items, source = "open-file")
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

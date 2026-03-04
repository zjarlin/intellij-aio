package site.addzero.diagnostic.service

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ex.InspectionManagerEx
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.openapi.compiler.CompilerTopics
import com.intellij.openapi.compiler.CompilationStatusListener
import com.intellij.openapi.compiler.CompileContext
import com.intellij.problems.ProblemListener
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.Alarm
import site.addzero.diagnostic.config.DiagnosticExclusionConfig
import site.addzero.diagnostic.model.DiagnosticItem
import site.addzero.diagnostic.model.DiagnosticSeverity
import site.addzero.diagnostic.model.FileDiagnostics
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.PROJECT)
class DiagnosticCollectorService(private val project: Project) : Disposable {

    private val listeners = CopyOnWriteArrayList<(List<FileDiagnostics>) -> Unit>()
    private val debounceAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

    // 定期自动扫描定时器（每30秒）
    private val periodicScanAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

    @Volatile
    private var latestDiagnostics: List<FileDiagnostics> = emptyList()

    // 批量扫描时缓存 PsiDocumentManager 以减少重复获取
    private val analyzedDocuments = ConcurrentHashMap<VirtualFile, List<DiagnosticItem>>()

    // 排除配置
    private val exclusionConfig: DiagnosticExclusionConfig by lazy {
        DiagnosticExclusionConfig.getInstance(project)
    }

    companion object {
        fun getInstance(project: Project): DiagnosticCollectorService =
            project.getService(DiagnosticCollectorService::class.java)

        // 快速排除的目录名称
        val EXCLUDED_DIR_NAMES = setOf(
            "build", "out", "target", "bin", "obj",
            "node_modules", ".gradle", ".idea", ".git",
            "generated", "gen", "logs", "tmp"
        )
    }

    init {
        setupListeners()
        startPeriodicScan()
    }

    /**
     * 启动定期自动扫描（每30秒）
     */
    private fun startPeriodicScan() {
        schedulePeriodicScan()
    }

    private fun schedulePeriodicScan() {
        periodicScanAlarm.addRequest({
            if (!project.isDisposed) {
                doCollect { diagnostics ->
                    latestDiagnostics = diagnostics
                    notifyListeners(diagnostics)
                }
                // 递归调度下一次扫描
                schedulePeriodicScan()
            }
        }, 30000) // 30秒间隔
    }

    private fun setupListeners() {
        val connection = project.messageBus.connect(this)

        connection.subscribe(
            DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC,
            object : DaemonCodeAnalyzer.DaemonListener {
                override fun daemonFinished(fileEditors: Collection<FileEditor>) {
                    scheduleCollect()
                }
            }
        )

        connection.subscribe(
            ProblemListener.TOPIC,
            object : ProblemListener {
                override fun problemsAppeared(file: VirtualFile) = scheduleCollect()
                override fun problemsDisappeared(file: VirtualFile) = scheduleCollect()
                override fun problemsChanged(file: VirtualFile) = scheduleCollect()
            }
        )

        // 方案2: 监听编译器消息 - 编译完成后只更新变更的文件
        connection.subscribe(
            CompilerTopics.COMPILATION_STATUS,
            object : CompilationStatusListener {
                override fun compilationFinished(aborted: Boolean, errors: Int, warnings: Int, compileContext: CompileContext) {
                    if (!aborted) {
                        // 获取本次编译涉及的文件并增量更新
                        scheduleIncrementalUpdate(compileContext)
                    }
                }
            }
        )
    }

    private fun scheduleCollect() {
        debounceAlarm.cancelAllRequests()
        debounceAlarm.addRequest({
            doCollect { diagnostics ->
                latestDiagnostics = diagnostics
                notifyListeners(diagnostics)
            }
        }, 1000)
    }

    /**
     * 编译完成后触发快速刷新
     * 编译器只告诉我们编译完成，不直接提供变更文件列表
     * 所以我们使用更短的延迟来快速刷新
     */
    private fun scheduleIncrementalUpdate(compileContext: CompileContext) {
        debounceAlarm.cancelAllRequests()
        debounceAlarm.addRequest({
            // 编译后触发一次快速全量收集
            doCollect { diagnostics ->
                latestDiagnostics = diagnostics
                notifyListeners(diagnostics)
            }
        }, 300) // 编译后使用更短的延迟
    }

    fun addListener(listener: (List<FileDiagnostics>) -> Unit) {
        listeners.add(listener)
        if (latestDiagnostics.isNotEmpty()) {
            ApplicationManager.getApplication().invokeLater { listener(latestDiagnostics) }
        }
    }

    fun removeListener(listener: (List<FileDiagnostics>) -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners(diagnostics: List<FileDiagnostics>) {
        ApplicationManager.getApplication().invokeLater {
            listeners.forEach { it(diagnostics) }
        }
    }

    override fun dispose() {
        listeners.clear()
    }

    fun collectDiagnostics(onComplete: (List<FileDiagnostics>) -> Unit) {
        doCollect(onComplete)
    }

    private fun doCollect(onComplete: (List<FileDiagnostics>) -> Unit) {
        val instance = DumbService.getInstance(project)
        if (instance.isDumb) {
            onComplete(emptyList())
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = ReadAction.compute<List<FileDiagnostics>, Throwable> {
                collectAllDiagnostics()
            }
            latestDiagnostics = result
            ApplicationManager.getApplication().invokeLater {
                onComplete(result)
            }
        }
    }

    /**
     * 批量收集所有诊断信息
     * 使用饿汉式扫描：主动分析所有源文件，而不是依赖已打开文件的缓存
     */
    private fun collectAllDiagnostics(): List<FileDiagnostics> {
        return collectAllDiagnosticsWithProgress(null)
    }

    /**
     * 带进度指示的批量收集
     */
    fun collectAllDiagnosticsWithProgress(indicator: ProgressIndicator? = null): List<FileDiagnostics> {
        val psiManager = PsiManager.getInstance(project)
        val fileDocumentManager = FileDocumentManager.getInstance()
        val psiDocumentManager = PsiDocumentManager.getInstance(project)
        val wolf = WolfTheProblemSolver.getInstance(project)
        val diagnosticsList = mutableListOf<FileDiagnostics>()

        // 清空缓存
        analyzedDocuments.clear()

        // 先收集所有要扫描的文件
        val filesToScan = mutableListOf<VirtualFile>()
        val sourceRoots = ProjectRootManager.getInstance(project).contentSourceRoots

        sourceRoots.forEach { root ->
            VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Unit>() {
                override fun visitFile(file: VirtualFile): Boolean {
                    if (file.isDirectory) {
                        return !shouldExcludeDirectory(file)
                    }
                    if (isSourceFile(file) && exclusionConfig.shouldScanFile(file)) {
                        filesToScan.add(file)
                    }
                    return true
                }
            })
        }

        // 更新进度
        indicator?.text = "正在扫描 ${filesToScan.size} 个文件..."
        val total = filesToScan.size

        // 扫描文件并更新进度
        filesToScan.forEachIndexed { index, file ->
            indicator?.let {
                it.fraction = index.toDouble() / total
                it.text2 = "正在分析: ${file.name}"
            }

            collectFileDiagnosticsBatch(file, psiManager, fileDocumentManager, psiDocumentManager, wolf)?.let {
                diagnosticsList.add(it)
            }

            // 每10个文件检查一次是否取消
            if (index % 10 == 0) {
                indicator?.checkCanceled()
            }
        }

        return diagnosticsList.sortedBy { it.file.path }
    }

    /**
     * 批量扫描时检查目录是否应该排除
     */
    private fun shouldExcludeDirectory(dir: VirtualFile): Boolean {
        // 快速排除常见目录
        val dirName = dir.name
        return dirName in EXCLUDED_DIR_NAMES
    }

    /**
     * 批量收集单个文件的诊断信息
     * 与 collectFileDiagnostics 不同，这个方法会主动触发分析
     */
    private fun collectFileDiagnosticsBatch(
        file: VirtualFile,
        psiManager: PsiManager,
        fileDocumentManager: FileDocumentManager,
        psiDocumentManager: PsiDocumentManager,
        wolf: WolfTheProblemSolver
    ): FileDiagnostics? {
        val psiFile = psiManager.findFile(file) ?: return null

        // 获取或创建文档
        var document: Document? = fileDocumentManager.getDocument(file)

        // 如果文档不存在，尝试通过 PsiFile 获取
        if (document == null) {
            document = psiDocumentManager.getDocument(psiFile)
        }

        // 如果仍然没有文档，无法进行分析
        if (document == null) {
            // 回退到 WolfTheProblemSolver 检查
            return if (wolf.isProblemFile(file)) {
                val item = DiagnosticItem(
                    file = file,
                    psiFile = psiFile,
                    lineNumber = 1,
                    message = "文件存在编译问题",
                    severity = DiagnosticSeverity.ERROR
                )
                FileDiagnostics(file, psiFile, listOf(item))
            } else null
        }

        // 收集高亮信息（不提交文档，避免线程问题）
        val items = try {
            collectHighlightsFromDocument(document, file, psiFile)
        } catch (e: Exception) {
            emptyList()
        }

        // 如果没有 highlights 但 wolf 标记为问题文件，也记录
        if (items.isEmpty() && wolf.isProblemFile(file)) {
            val item = DiagnosticItem(
                file = file,
                psiFile = psiFile,
                lineNumber = 1,
                message = "文件存在编译问题",
                severity = DiagnosticSeverity.ERROR
            )
            return FileDiagnostics(file, psiFile, listOf(item))
        }

        return if (items.isNotEmpty()) {
            FileDiagnostics(file, psiFile, items)
        } else null
    }

    /**
     * 从文档中收集高亮信息
     * 后台扫描策略：直接使用代码检查，不依赖 DaemonCodeAnalyzer 缓存
     */
    private fun collectHighlightsFromDocument(
        document: Document,
        file: VirtualFile,
        psiFile: PsiFile
    ): List<DiagnosticItem> {
        val items = mutableListOf<DiagnosticItem>()

        try {
            // 方法1：如果文件当前正在编辑器中打开，使用已有高亮
            val openFiles = FileEditorManager.getInstance(project).openFiles
            if (file in openFiles) {
                items.addAll(getHighlightsFromOpenFile(document, file, psiFile))
            }

            // 方法2：直接运行本地代码检查（后台扫描的主要方式）
            if (items.isEmpty()) {
                items.addAll(runLocalInspection(file, psiFile, document))
            }

        } catch (e: Exception) {
            // 静默处理异常
        }

        return items
    }

    /**
     * 从已打开的文件获取高亮信息
     */
    private fun getHighlightsFromOpenFile(
        document: Document,
        file: VirtualFile,
        psiFile: PsiFile
    ): List<DiagnosticItem> {
        return try {
            // 使用编辑器标记模型获取高亮
            val editor = FileEditorManager.getInstance(project).getSelectedEditor(file)
            if (editor != null) {
                // 文件已打开，编辑器有高亮信息
                // 这里我们依赖 FileDocumentManager 的缓存
                getHighlightsFromMarkupModel(document, file, psiFile)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 从标记模型获取高亮
     */
    private fun getHighlightsFromMarkupModel(
        document: Document,
        file: VirtualFile,
        psiFile: PsiFile
    ): List<DiagnosticItem> {
        val items = mutableListOf<DiagnosticItem>()

        try {
            // 使用 DaemonCodeAnalyzerImpl 获取高亮信息
            val daemonImpl = DaemonCodeAnalyzer.getInstance(project) as? DaemonCodeAnalyzerImpl

            // 获取文件的高亮信息
            val highlightInfos = try {
                // 尝试从 DaemonCodeAnalyzerImpl 获取
                val method = DaemonCodeAnalyzerImpl::class.java.getDeclaredMethod(
                    "getFileLevelHighlights",
                    Project::class.java,
                    PsiFile::class.java
                )
                method.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val highlights = method.invoke(daemonImpl, project, psiFile) as? List<HighlightInfo>
                highlights ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }

            highlightInfos.forEach { info ->
                if (info.severity >= HighlightSeverity.WARNING) {
                    convertHighlightToItem(file, psiFile, document, info)?.let {
                        items.add(it)
                    }
                }
            }
        } catch (e: Exception) {
            // 静默处理
        }

        return items
    }

    /**
     * 运行本地检查获取问题
     */
    private fun runLocalInspection(
        file: VirtualFile,
        psiFile: PsiFile,
        document: Document
    ): List<DiagnosticItem> {
        val items = mutableListOf<DiagnosticItem>()

        try {
            val inspectionManager = InspectionManager.getInstance(project) as InspectionManagerEx
            val profile = InspectionProjectProfileManager.getInstance(project).currentProfile as InspectionProfileImpl

            val tools = profile.getAllEnabledInspectionTools(project)
                .filter { it.tool is com.intellij.codeInspection.LocalInspectionTool }

            tools.forEach { toolState ->
                try {
                    val tool = toolState.tool.tool as com.intellij.codeInspection.LocalInspectionTool
                    val holder = com.intellij.codeInspection.ProblemsHolder(
                        inspectionManager,
                        psiFile,
                        false
                    )

                    val visitor = tool.buildVisitor(holder, false)
                    psiFile.accept(visitor)

                    holder.results.forEach { descriptor ->
                        val lineNumber = descriptor.lineNumber + 1
                        val severity = when (descriptor.highlightType) {
                            ProblemHighlightType.ERROR, ProblemHighlightType.GENERIC_ERROR -> DiagnosticSeverity.ERROR
                            else -> DiagnosticSeverity.WARNING
                        }

                        // 检查排除规则
                        if (!shouldExcludeProblem(descriptor.descriptionTemplate, file)) {
                            val item = DiagnosticItem(
                                file = file,
                                psiFile = psiFile,
                                lineNumber = lineNumber,
                                message = descriptor.descriptionTemplate,
                                severity = severity
                            )
                            items.add(item)
                        }
                    }
                } catch (_: Exception) {
                    // 单个检查失败继续下一个
                }
            }
        } catch (e: Exception) {
            // 静默处理
        }

        return items
    }

    /**
     * 检查问题是否应该被排除
     */
    private fun shouldExcludeProblem(message: String, file: VirtualFile): Boolean {
        // 可以在这里添加基于消息内容的排除逻辑
        return false
    }

    /**
     * 将 HighlightInfo 转换为 DiagnosticItem
     */
    private fun convertHighlightToItem(
        file: VirtualFile,
        psiFile: PsiFile,
        document: Document,
        info: HighlightInfo
    ): DiagnosticItem? {
        val message = info.description ?: return null
        val lineNumber = try {
            document.getLineNumber(info.startOffset) + 1
        } catch (e: Exception) {
            1
        }
        val severity = when {
            info.severity >= HighlightSeverity.ERROR -> DiagnosticSeverity.ERROR
            else -> DiagnosticSeverity.WARNING
        }
        return DiagnosticItem(file, psiFile, lineNumber, message, severity)
    }

    private fun isSourceFile(file: VirtualFile): Boolean {
        val extension = file.extension?.lowercase() ?: return false
        return extension in setOf("java", "kt", "kts", "groovy", "scala")
    }

    fun getErrorFiles(diagnostics: List<FileDiagnostics>): List<FileDiagnostics> =
        diagnostics.filter { it.hasErrors }
            .map { fileDiag ->
                fileDiag.copy(items = fileDiag.items.filter { it.severity == DiagnosticSeverity.ERROR })
            }

    fun getWarningFiles(diagnostics: List<FileDiagnostics>): List<FileDiagnostics> =
        diagnostics.filter { it.hasWarnings }
            .map { fileDiag ->
                fileDiag.copy(items = fileDiag.items.filter { it.severity == DiagnosticSeverity.WARNING })
            }

    /**
     * 使用 GlobalInspectionContext + AnalysisScope 批量运行检查
     * 这是更全面的扫描方式，会运行所有启用的 Inspection
     */
    fun runGlobalInspection(onComplete: (List<FileDiagnostics>) -> Unit) {
        if (DumbService.getInstance(project).isDumb) {
            onComplete(emptyList())
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Running Global Inspections...", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val result = doRunGlobalInspection(indicator)
                    ApplicationManager.getApplication().invokeLater {
                        latestDiagnostics = result
                        onComplete(result)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    ApplicationManager.getApplication().invokeLater {
                        onComplete(emptyList())
                    }
                }
            }
        })
    }

    private fun doRunGlobalInspection(indicator: ProgressIndicator): List<FileDiagnostics> {
        val scope = AnalysisScope(project)
        val inspectionManager = InspectionManager.getInstance(project) as InspectionManagerEx
        val profile = InspectionProjectProfileManager.getInstance(project).currentProfile as InspectionProfileImpl

        val problemsByFile = mutableMapOf<VirtualFile, MutableList<DiagnosticItem>>()
        val psiManager = PsiManager.getInstance(project)

        ReadAction.run<Throwable> {
            val tools = profile.getAllEnabledInspectionTools(project)
            val totalTools = tools.size

            tools.forEachIndexed { index, toolState ->
                if (indicator.isCanceled) return@run

                indicator.fraction = index.toDouble() / totalTools
                val wrapper = toolState.tool
                indicator.text2 = "Running: ${wrapper.shortName}"

                runInspectionTool(wrapper, scope, psiManager, problemsByFile)
            }
        }

        return problemsByFile.map { (vFile, items) ->
            val psiFile = psiManager.findFile(vFile)!!
            FileDiagnostics(vFile, psiFile, items.sortedBy { it.lineNumber })
        }.sortedBy { it.file.path }
    }

    private fun runInspectionTool(
        toolWrapper: InspectionToolWrapper<*, *>,
        scope: AnalysisScope,
        psiManager: PsiManager,
        problemsByFile: MutableMap<VirtualFile, MutableList<DiagnosticItem>>
    ) {
        try {
            val tool = toolWrapper.tool

            if (tool is com.intellij.codeInspection.LocalInspectionTool) {
                scope.accept { file ->
                    if (!isSourceFile(file)) return@accept true

                    val psiFile = psiManager.findFile(file) ?: return@accept true
                    val holder = com.intellij.codeInspection.ProblemsHolder(
                        InspectionManager.getInstance(project),
                        psiFile,
                        false
                    )

                    try {
                        val visitor = tool.buildVisitor(holder, false)
                        psiFile.accept(visitor)

                        holder.results.forEach { descriptor ->
                            val vFile = psiFile.virtualFile ?: return@forEach
                            val lineNumber = descriptor.lineNumber + 1
                            val severity = when (descriptor.highlightType) {
                                ProblemHighlightType.ERROR, ProblemHighlightType.GENERIC_ERROR -> DiagnosticSeverity.ERROR
                                else -> DiagnosticSeverity.WARNING
                            }
                            val item = DiagnosticItem(vFile, psiFile, lineNumber, descriptor.descriptionTemplate, severity)
                            problemsByFile.getOrPut(vFile) { mutableListOf() }.add(item)
                        }
                    } catch (_: Exception) {
                    }
                    true
                }
            }
        } catch (_: Exception) {
        }
    }
}

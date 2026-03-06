package site.addzero.diagnostic.service

import com.intellij.ProjectTopics
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.openapi.compiler.CompilerTopics
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.compiler.CompilerMessage
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.compiler.CompilationStatusListener
import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.problems.ProblemListener
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.Alarm
import site.addzero.diagnostic.config.DiagnosticExclusionConfig
import site.addzero.diagnostic.core.collectDiagnostics
import site.addzero.diagnostic.model.FileDiagnostics
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * 诊断信息包装类，解决 ConcurrentHashMap 不能存 null 的问题
 */
private class DiagnosticResult(val diagnostics: FileDiagnostics?)

/**
 * 诊断收集服务 - 重新设计版
 *
 * 核心设计：
 * 1. 启动时全量扫描所有文件，建立完整缓存（无论是否有问题）
 * 2. 维护 needScanQueue，文件变更时加入队列
 * 3. 增量扫描只处理变更文件
 * 4. 文件关闭后缓存不丢失
 */
@Service(Service.Level.PROJECT)
class DiagnosticCollectorService(private val project: Project) : AutoCloseable {

    // 完整缓存：所有已扫描文件的诊断信息（包括无问题的文件，diagnostics=null表示无问题）
    private val diagnosticsCache = ConcurrentHashMap<VirtualFile, DiagnosticResult>()
    private val compileDiagnosticsCache = ConcurrentHashMap<VirtualFile, FileDiagnostics>()

    // 待扫描队列：文件变更时加入此队列
    private val needScanQueue = LinkedBlockingQueue<VirtualFile>()

    // 监听器
    private val listeners = CopyOnWriteArrayList<(List<FileDiagnostics>) -> Unit>()
    private val progressListeners = CopyOnWriteArrayList<(ScanProgress) -> Unit>()

    // 扫描控制
    private val isScanning = AtomicBoolean(false)
    private val isFullScanning = AtomicBoolean(false)
    private val fullScanRerunRequested = AtomicBoolean(false)
    private val compileTriggeredOnce = AtomicBoolean(false)
    private val debounceAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, project)
    private val fullScanRetryAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, project)

    // 进度跟踪
    private val scanProgress = AtomicReference(ScanProgress())

    data class ScanProgress(
        val currentFile: String = "",
        val scannedCount: Int = 0,
        val totalCount: Int = 0,
        val isScanning: Boolean = false,
        val isFullScan: Boolean = false
    )

    private val exclusionConfig: DiagnosticExclusionConfig by lazy {
        DiagnosticExclusionConfig.getInstance(project)
    }

    companion object {
        private val LOG: Logger = Logger.getInstance(DiagnosticCollectorService::class.java)

        fun getInstance(project: Project): DiagnosticCollectorService =
            project.getService(DiagnosticCollectorService::class.java)

        val EXCLUDED_DIR_NAMES = setOf(
            "build", "out", "target", "bin", "obj",
            "node_modules", ".gradle", ".idea", ".git",
            "generated", "gen", "logs", "tmp"
        )
    }

    init {
        setupListeners()
    }

    // ==================== 公共API ====================

    /**
     * 启动时全量扫描 - 饿汉式
     * 扫描所有文件，建立完整缓存
     */
    fun performFullScan(indicator: ProgressIndicator? = null) {
        if (DumbService.getInstance(project).isDumb) {
            LOG.debug("[Problem4AI][Scan] skip full scan in dumb mode, reschedule when smart")
            DumbService.getInstance(project).runWhenSmart {
                performFullScan(indicator)
            }
            return
        }

        if (isFullScanning.compareAndSet(false, true)) {
            LOG.info("[Problem4AI][Scan] full scan requested, start async")
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    doFullScan(indicator)
                } finally {
                    isFullScanning.set(false)
                    if (fullScanRerunRequested.compareAndSet(true, false)) {
                        LOG.info("[Problem4AI][Scan] rerun full scan because request arrived during running scan")
                        performFullScan(indicator)
                    }
                }
            }
        } else {
            LOG.debug("[Problem4AI][Scan] full scan already running, mark rerun")
            fullScanRerunRequested.set(true)
        }
    }

    /**
     * 触发增量扫描 - 处理 needScanQueue 中的文件
     */
    fun triggerIncrementalScan() {
        if (isScanning.compareAndSet(false, true)) {
            LOG.debug("[Problem4AI][Scan] incremental scan triggered")
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    processNeedScanQueue()
                } finally {
                    isScanning.set(false)
                }
            }
        } else {
            LOG.debug("[Problem4AI][Scan] incremental scan ignored because scanner is busy")
        }
    }

    /**
     * 将文件加入待扫描队列
     */
    fun queueForScan(file: VirtualFile) {
        if (!file.isValid || file.isDirectory) {
            LOG.debug("[Problem4AI][Queue] skip invalid/dir: ${file.path}")
            return
        }
        if (!isSourceFile(file)) {
            LOG.debug("[Problem4AI][Queue] skip extension: ${file.path}, ext=${file.extension ?: "<none>"}")
            return
        }
        if (exclusionConfig.isExcluded(file)) {
            LOG.debug("[Problem4AI][Queue] skip excluded: ${file.path}")
            return
        }

        needScanQueue.offer(file)
        LOG.debug("[Problem4AI][Queue] queued: ${file.path}, pending=${needScanQueue.size}")

        // 防抖触发增量扫描
        debounceAlarm.cancelAllRequests()
        debounceAlarm.addRequest({
            triggerIncrementalScan()
        }, 500)
    }

    fun addListener(listener: (List<FileDiagnostics>) -> Unit) {
        listeners.add(listener)
        // 立即通知当前缓存的状态
        val currentProblems = getAllProblemDiagnostics()
        if (currentProblems.isNotEmpty()) {
            ApplicationManager.getApplication().invokeLater {
                listener(currentProblems)
            }
        }
    }

    fun removeListener(listener: (List<FileDiagnostics>) -> Unit) {
        listeners.remove(listener)
    }

    fun addProgressListener(listener: (ScanProgress) -> Unit) {
        progressListeners.add(listener)
    }

    fun removeProgressListener(listener: (ScanProgress) -> Unit) {
        progressListeners.remove(listener)
    }

    /**
     * 获取所有有问题的诊断（用于面板显示）
     */
    fun getAllProblemDiagnostics(): List<FileDiagnostics> {
        val allFiles = LinkedHashSet<VirtualFile>()
        allFiles.addAll(diagnosticsCache.keys)
        allFiles.addAll(compileDiagnosticsCache.keys)

        return allFiles
            .mapNotNull { file -> mergeFileDiagnostics(file) }
            .filter { it.items.isNotEmpty() }
            .sortedBy { it.file.path }
    }

    /**
     * 获取指定文件的诊断（可能返回null表示无问题或尚未扫描）
     */
    fun getDiagnostics(file: VirtualFile): FileDiagnostics? {
        return mergeFileDiagnostics(file)
    }

    override fun close() {
        listeners.clear()
        progressListeners.clear()
        debounceAlarm.dispose()
        fullScanRetryAlarm.dispose()
    }

    // ==================== 内部实现 ====================

    private fun setupListeners() {
        val connection = project.messageBus.connect(project)
        LOG.info("[Problem4AI][Listener] setup listeners for project=${project.name}")

        // 监听 DaemonCodeAnalyzer 完成 - 文件被分析后触发
        connection.subscribe(
            DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC,
            object : DaemonCodeAnalyzer.DaemonListener {
                override fun daemonFinished(fileEditors: Collection<FileEditor>) {
                    LOG.debug("[Problem4AI][Listener] daemonFinished editors=${fileEditors.size}")
                    // 将已打开的文件加入扫描队列
                    fileEditors.forEach { editor ->
                        editor.file?.let { queueForScan(it) }
                    }
                }
            }
        )

        // 监听 Problems 系统变化
        connection.subscribe(
            ProblemListener.TOPIC,
            object : ProblemListener {
                override fun problemsAppeared(file: VirtualFile) {
                    LOG.debug("[Problem4AI][Listener] problemsAppeared: ${file.path}")
                    queueForScan(file)
                }
                override fun problemsDisappeared(file: VirtualFile) {
                    LOG.debug("[Problem4AI][Listener] problemsDisappeared: ${file.path}")
                    queueForScan(file)
                }
                override fun problemsChanged(file: VirtualFile) {
                    LOG.debug("[Problem4AI][Listener] problemsChanged: ${file.path}")
                    queueForScan(file)
                }
            }
        )

        // 监听编译完成
        connection.subscribe(
            CompilerTopics.COMPILATION_STATUS,
            object : CompilationStatusListener {
                override fun compilationFinished(
                    aborted: Boolean,
                    errors: Int,
                    warnings: Int,
                    compileContext: CompileContext
                ) {
                    applyCompileContextMessages(compileContext)
                    if (!aborted) {
                        LOG.info("[Problem4AI][Listener] compilation finished, requeue scanned files=${diagnosticsCache.size}")
                        // 编译完成后，将所有已扫描的文件重新加入队列
                        diagnosticsCache.keys.forEach { queueForScan(it) }
                    }
                }
            }
        )

        // 监听项目根变化（如新项目导入完成、模块加载完成）
        connection.subscribe(
            ProjectTopics.PROJECT_ROOTS,
            object : ModuleRootListener {
                override fun rootsChanged(event: ModuleRootEvent) {
                    LOG.info("[Problem4AI][Listener] project roots changed, schedule full scan")
                    fullScanRetryAlarm.cancelAllRequests()
                    fullScanRetryAlarm.addRequest({
                        performFullScan()
                    }, 1000)
                }
            }
        )

        // 监听文件系统变化，保证未打开文件修改后也会刷新缓存
        connection.subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    LOG.debug("[Problem4AI][Listener] VFS changes count=${events.size}")
                    handleVfsChanges(events)
                }
            }
        )
    }

    /**
     * 执行全量扫描
     */
    private fun doFullScan(indicator: ProgressIndicator?) {
        updateProgress(ScanProgress(isScanning = true, isFullScan = true))
        LOG.info("[Problem4AI][Scan] full scan started")

        // 收集所有要扫描的文件
        val filesToScan = ReadAction.compute<List<VirtualFile>, Throwable> {
            collectAllSourceFiles()
        }

        if (filesToScan.isEmpty()) {
            LOG.warn("[Problem4AI][Scan] full scan found 0 files, schedule retry")
            updateProgress(ScanProgress())
            scheduleFullScanRetry()
            return
        }
        LOG.info("[Problem4AI][Scan] full scan files=${filesToScan.size}")

        val total = filesToScan.size
        val scanned = AtomicInteger(0)
        val batchSize = 10

        indicator?.let {
            it.text = "正在扫描 $total 个文件..."
            it.isIndeterminate = false
            it.fraction = 0.0
        }

        // 分批扫描
        filesToScan.chunked(batchSize).forEach { batch ->
            if (indicator?.isCanceled == true) return@forEach
            if (DumbService.getInstance(project).isDumb) {
                LOG.info("[Problem4AI][Scan] switch to dumb mode during full scan, reschedule")
                updateProgress(ScanProgress())
                performFullScan()
                return
            }

            // 扫描这一批
            val results = batch.associateWith { file ->
                collectDiagnosticsSafely(file)
            }

            // 更新缓存（包装成 DiagnosticResult 避免 null）
            results.forEach { (file, diag) ->
                diagnosticsCache[file] = DiagnosticResult(diag)
            }
            val batchWithProblems = results.count { it.value?.items?.isNotEmpty() == true }
            LOG.debug(
                "[Problem4AI][Scan] full batch processed=${batch.size}, withProblems=$batchWithProblems, cacheSize=${diagnosticsCache.size}"
            )

            // 更新进度
            val processed = scanned.addAndGet(batch.size)
            val progress = ScanProgress(
                currentFile = batch.lastOrNull()?.name ?: "",
                scannedCount = processed,
                totalCount = total,
                isScanning = true,
                isFullScan = true
            )
            updateProgress(progress)

            // 分批通知监听器，避免“必须等扫完才看到结果”
            notifyDiagnosticsListeners()

            indicator?.let {
                it.fraction = processed.toDouble() / total
                it.text2 = "${batch.lastOrNull()?.name} ($processed/$total)"
            }

            // 每批后让出时间片
            if (processed < total) {
                Thread.sleep(5)
            }
        }

        // 扫描完成
        updateProgress(ScanProgress())
        LOG.info("[Problem4AI][Scan] full scan completed, problems=${getAllProblemDiagnostics().size}, cacheSize=${diagnosticsCache.size}")
        maybeTriggerBackgroundCompile()

        // 扫描完成后再通知一次，确保最终一致
        notifyDiagnosticsListeners()
    }

    /**
     * 处理待扫描队列（增量扫描）
     */
    private fun processNeedScanQueue() {
        val filesToScan = mutableListOf<VirtualFile>()

        // 取出队列中所有文件
        while (true) {
            val file = needScanQueue.poll() ?: break
            // 去重
            if (file !in filesToScan) {
                filesToScan.add(file)
            }
        }

        if (filesToScan.isEmpty()) return
        if (DumbService.getInstance(project).isDumb) {
            LOG.debug("[Problem4AI][Scan] incremental paused in dumb mode, requeue files=${filesToScan.size}")
            filesToScan.forEach { needScanQueue.offer(it) }
            return
        }
        LOG.debug("[Problem4AI][Scan] incremental start files=${filesToScan.size}")

        updateProgress(ScanProgress(isScanning = true, isFullScan = false))

        val batchSize = 5
        filesToScan.chunked(batchSize).forEachIndexed { index, batch ->
            // 扫描这一批
            val results = batch.associateWith { file ->
                collectDiagnosticsSafely(file)
            }

            // 更新缓存（包装成 DiagnosticResult 避免 null）
            results.forEach { (file, diag) ->
                diagnosticsCache[file] = DiagnosticResult(diag)
            }
            val batchWithProblems = results.count { it.value?.items?.isNotEmpty() == true }
            LOG.debug(
                "[Problem4AI][Scan] incremental batch processed=${batch.size}, withProblems=$batchWithProblems, cacheSize=${diagnosticsCache.size}"
            )

            // 更新进度
            val progress = ScanProgress(
                currentFile = batch.lastOrNull()?.name ?: "",
                scannedCount = (index + 1) * batch.size,
                totalCount = filesToScan.size,
                isScanning = true,
                isFullScan = false
            )
            updateProgress(progress)

            // 增量扫描也分批通知一次，快速反馈面板
            notifyDiagnosticsListeners()

            Thread.sleep(5)
        }

        updateProgress(ScanProgress())
        LOG.debug("[Problem4AI][Scan] incremental completed, problems=${getAllProblemDiagnostics().size}")

        // 扫描完成后再通知一次，确保最终一致
        notifyDiagnosticsListeners()
    }

    private fun notifyDiagnosticsListeners() {
        val problems = getAllProblemDiagnostics()
        LOG.debug("[Problem4AI][UI] notify listeners=${listeners.size}, problemFiles=${problems.size}")
        ApplicationManager.getApplication().invokeLater {
            listeners.forEach { it(problems) }
        }
    }

    private fun collectDiagnosticsSafely(file: VirtualFile): FileDiagnostics? {
        return try {
            ReadAction.compute<FileDiagnostics?, Throwable> {
                file.collectDiagnostics(project)
            }
        } catch (_: ProcessCanceledException) {
            null
        } catch (e: Exception) {
            LOG.debug("[Problem4AI][Scan] collect failed for file=${file.path}", e)
            null
        }
    }

    private fun mergeFileDiagnostics(file: VirtualFile): FileDiagnostics? {
        val scanDiagnostics = diagnosticsCache[file]?.diagnostics
        val compileDiagnostics = compileDiagnosticsCache[file]
        return mergeDiagnostics(scanDiagnostics, compileDiagnostics)
    }

    private fun mergeDiagnostics(
        scanDiagnostics: FileDiagnostics?,
        compileDiagnostics: FileDiagnostics?
    ): FileDiagnostics? {
        if (scanDiagnostics == null) return compileDiagnostics
        if (compileDiagnostics == null) return scanDiagnostics

        val mergedItems = (scanDiagnostics.items + compileDiagnostics.items)
            .distinctBy { Triple(it.lineNumber, it.message, it.severity) }
            .sortedBy { it.lineNumber }

        return FileDiagnostics(
            file = scanDiagnostics.file,
            psiFile = scanDiagnostics.psiFile ?: compileDiagnostics.psiFile,
            items = mergedItems
        )
    }

    private fun applyCompileContextMessages(compileContext: CompileContext) {
        val grouped = LinkedHashMap<VirtualFile, MutableList<site.addzero.diagnostic.model.DiagnosticItem>>()

        fun collectByCategory(category: CompilerMessageCategory, severity: site.addzero.diagnostic.model.DiagnosticSeverity) {
            compileContext.getMessages(category).forEach { message ->
                val file = message.virtualFile ?: return@forEach
                if (!file.isValid || file.isDirectory) return@forEach
                if (!isSourceFile(file) || exclusionConfig.isExcluded(file)) return@forEach

                val line = extractLineFromCompilerMessage(message)
                val text = message.message?.takeIf { it.isNotBlank() } ?: return@forEach
                val list = grouped.getOrPut(file) { mutableListOf() }
                if (list.none { it.lineNumber == line && it.message == text && it.severity == severity }) {
                    list.add(
                        site.addzero.diagnostic.model.DiagnosticItem(
                            file = file,
                            psiFile = null,
                            lineNumber = line,
                            message = text,
                            severity = severity
                        )
                    )
                }
            }
        }

        collectByCategory(CompilerMessageCategory.ERROR, site.addzero.diagnostic.model.DiagnosticSeverity.ERROR)
        collectByCategory(CompilerMessageCategory.WARNING, site.addzero.diagnostic.model.DiagnosticSeverity.WARNING)

        compileDiagnosticsCache.clear()
        grouped.forEach { (file, items) ->
            compileDiagnosticsCache[file] = FileDiagnostics(
                file = file,
                psiFile = null,
                items = items.sortedBy { it.lineNumber }
            )
        }

        val errorCount = compileDiagnosticsCache.values.sumOf {
            it.items.count { item -> item.severity == site.addzero.diagnostic.model.DiagnosticSeverity.ERROR }
        }
        val warningCount = compileDiagnosticsCache.values.sumOf {
            it.items.count { item -> item.severity == site.addzero.diagnostic.model.DiagnosticSeverity.WARNING }
        }
        LOG.info(
            "[Problem4AI][Compile] compile context applied files=${compileDiagnosticsCache.size} errors=$errorCount warnings=$warningCount"
        )
        notifyDiagnosticsListeners()
    }

    private fun maybeTriggerBackgroundCompile() {
        if (!compileTriggeredOnce.compareAndSet(false, true)) {
            return
        }

        if (DumbService.getInstance(project).isDumb) {
            compileTriggeredOnce.set(false)
            LOG.debug("[Problem4AI][Compile] skip background make in dumb mode, will retry later")
            return
        }

        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) {
                return@invokeLater
            }
            LOG.info("[Problem4AI][Compile] trigger background make to collect unopened-file compile diagnostics")
            CompilerManager.getInstance(project).make { aborted, errors, warnings, _ ->
                LOG.info("[Problem4AI][Compile] background make completed aborted=$aborted errors=$errors warnings=$warnings")
                if (aborted) {
                    compileTriggeredOnce.set(false)
                    fullScanRetryAlarm.cancelAllRequests()
                    fullScanRetryAlarm.addRequest({
                        maybeTriggerBackgroundCompile()
                    }, 2000)
                    LOG.info("[Problem4AI][Compile] background make aborted, schedule retry in 2000ms")
                }
            }
        }
    }

    private fun extractLineFromCompilerMessage(message: CompilerMessage): Int {
        val descriptor = message.navigatable as? OpenFileDescriptor
        val line = descriptor?.line ?: -1
        return if (line >= 0) line + 1 else 1
    }

    private fun handleVfsChanges(events: List<VFileEvent>) {
        if (events.isEmpty()) return

        var cacheChanged = false
        val changedFiles = LinkedHashSet<VirtualFile>()

        events.forEach { event ->
            val file = event.file ?: return@forEach

            if (event is VFileDeleteEvent || !file.isValid) {
                if (removeFromCache(file)) {
                    cacheChanged = true
                }
                return@forEach
            }

            if (file.isDirectory) return@forEach

            if (!isSourceFile(file) || exclusionConfig.isExcluded(file)) {
                if (removeFromCache(file)) {
                    cacheChanged = true
                }
                return@forEach
            }

            changedFiles.add(file)
        }

        changedFiles.forEach { queueForScan(it) }
        if (changedFiles.isNotEmpty()) {
            LOG.debug("[Problem4AI][Listener] VFS changed source files=${changedFiles.size}")
        }

        if (cacheChanged) {
            LOG.debug("[Problem4AI][Cache] cache entries removed by VFS event")
            notifyDiagnosticsListeners()
        }
    }

    private fun removeFromCache(file: VirtualFile): Boolean {
        val removedByKey = diagnosticsCache.remove(file) != null
        val removedCompileByKey = compileDiagnosticsCache.remove(file) != null
        val removedByPath = diagnosticsCache.keys.removeIf { cached -> cached.path == file.path }
        val removedCompileByPath = compileDiagnosticsCache.keys.removeIf { cached -> cached.path == file.path }
        if (removedByKey || removedByPath || removedCompileByKey || removedCompileByPath) {
            LOG.debug("[Problem4AI][Cache] removed stale cache for ${file.path}")
        }
        return removedByKey || removedByPath || removedCompileByKey || removedCompileByPath
    }

    private fun scheduleFullScanRetry() {
        fullScanRetryAlarm.cancelAllRequests()
        LOG.info("[Problem4AI][Scan] schedule full scan retry in 1500ms")
        fullScanRetryAlarm.addRequest({
            performFullScan()
        }, 1500)
    }

    /**
     * 收集所有源文件（用于全量扫描）
     * 遍历所有内容根（contentRoots），不只是源码根（sourceRoots）
     */
    private fun collectAllSourceFiles(): List<VirtualFile> {
        val files = mutableListOf<VirtualFile>()
        val projectRootManager = ProjectRootManager.getInstance(project)

        // 使用 contentRoots 而不是 contentSourceRoots，以包含项目中的所有文件
        val contentRoots = projectRootManager.contentRoots.toList()

        // 如果没有内容根，回退到使用 contentSourceRoots
        val rootsToScan = if (contentRoots.isEmpty()) {
            projectRootManager.contentSourceRoots.toList()
        } else {
            contentRoots
        }

        // 对于新项目初始化早期，根模型可能暂时为空，兜底使用项目根目录
        val fallbackRoots = if (rootsToScan.isEmpty()) {
            val basePath = project.basePath
            if (basePath.isNullOrBlank()) {
                emptyList()
            } else {
                listOfNotNull(LocalFileSystem.getInstance().findFileByPath(basePath))
            }
        } else {
            emptyList()
        }

        val resolvedRoots = (rootsToScan + fallbackRoots).distinctBy { it.path }
        LOG.info("[Problem4AI][Scan] collect roots count=${resolvedRoots.size}")
        resolvedRoots.forEach { root ->
            if (!root.isValid || !root.exists()) return@forEach

            VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Unit>() {
                override fun visitFile(file: VirtualFile): Boolean {
                    if (!file.isValid) return false
                    if (file.isDirectory) return !shouldExcludeDirectory(file)
                    if (isSourceFile(file) && exclusionConfig.shouldScanFile(file)) {
                        files.add(file)
                    }
                    return true
                }
            })
        }

        LOG.info("[Problem4AI][Scan] collect source files count=${files.size}")
        return files
    }

    private fun updateProgress(progress: ScanProgress) {
        scanProgress.set(progress)
        ApplicationManager.getApplication().invokeLater {
            progressListeners.forEach { it(progress) }
        }
    }

    private fun shouldExcludeDirectory(dir: VirtualFile): Boolean {
        return dir.name in EXCLUDED_DIR_NAMES
    }

    private fun isSourceFile(file: VirtualFile): Boolean {
        return exclusionConfig.isEnabledExtension(file)
    }
}

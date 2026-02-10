package site.addzero.problem2prompt.diagnostic

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.problems.ProblemListener
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.PsiManager
import com.intellij.util.Alarm
import site.addzero.problem2prompt.diagnostic.model.DiagnosticItem
import site.addzero.problem2prompt.diagnostic.model.DiagnosticSeverity
import site.addzero.problem2prompt.diagnostic.model.FileDiagnostics
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Project-level service that collects diagnostics from the IDE's code analysis infrastructure.
 *
 * Design extracted from problem4ai's DiagnosticCollectorService, simplified for problem2prompt:
 * - Uses file path strings (not VirtualFile) in the data models
 * - No `runGlobalInspection` method — only DocumentMarkupModel + WolfTheProblemSolver
 * - No `getErrorFiles`/`getWarningFiles` helpers — those belong in DiagnosticCacheService
 *
 * Listens to [DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC] and [ProblemListener.TOPIC] for
 * incremental updates, with debounced collection via [Alarm].
 */
@Service(Service.Level.PROJECT)
class DiagnosticCollectorService(private val project: Project) : Disposable {

    private val listeners = CopyOnWriteArrayList<(List<FileDiagnostics>) -> Unit>()
    private val debounceAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

    @Volatile
    private var latestDiagnostics: List<FileDiagnostics> = emptyList()

    companion object {
        /** Supported source file extensions for diagnostic collection. */
        private val SUPPORTED_EXTENSIONS = setOf("java", "kt", "kts", "groovy", "scala")

        /** Debounce delay in milliseconds before triggering collection after an event. */
        private const val DEBOUNCE_DELAY_MS = 1000

        fun getInstance(project: Project): DiagnosticCollectorService =
            project.getService(DiagnosticCollectorService::class.java)
    }

    init {
        setupListeners()
    }

    /**
     * Subscribe to IDE events that indicate diagnostic state may have changed.
     */
    private fun setupListeners() {
        val connection = project.messageBus.connect(this)

        // Listen for daemon code analyzer finishing analysis on file editors
        connection.subscribe(
            DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC,
            object : DaemonCodeAnalyzer.DaemonListener {
                override fun daemonFinished(fileEditors: Collection<FileEditor>) {
                    scheduleCollect()
                }
            }
        )

        // Listen for problem solver events (problems appeared/disappeared/changed)
        connection.subscribe(
            ProblemListener.TOPIC,
            object : ProblemListener {
                override fun problemsAppeared(file: VirtualFile) = scheduleCollect()
                override fun problemsDisappeared(file: VirtualFile) = scheduleCollect()
                override fun problemsChanged(file: VirtualFile) = scheduleCollect()
            }
        )
    }

    /**
     * Schedule a debounced diagnostic collection.
     * Cancels any pending collection and schedules a new one after [DEBOUNCE_DELAY_MS].
     */
    private fun scheduleCollect() {
        debounceAlarm.cancelAllRequests()
        debounceAlarm.addRequest({
            doCollect { diagnostics ->
                latestDiagnostics = diagnostics
                notifyListeners(diagnostics)
            }
        }, DEBOUNCE_DELAY_MS)
    }

    /**
     * Manually trigger diagnostic collection.
     * Useful for initial full scan or on-demand refresh.
     *
     * @param onComplete callback invoked with the collected diagnostics
     */
    fun collectDiagnostics(onComplete: (List<FileDiagnostics>) -> Unit) {
        doCollect(onComplete)
    }

    /**
     * Register a listener that will be notified whenever diagnostics are updated.
     * If diagnostics have already been collected, the listener is immediately
     * invoked with the latest data.
     *
     * @param listener callback receiving the full list of [FileDiagnostics]
     */
    fun addListener(listener: (List<FileDiagnostics>) -> Unit) {
        listeners.add(listener)
        if (latestDiagnostics.isNotEmpty()) {
            ApplicationManager.getApplication().invokeLater { listener(latestDiagnostics) }
        }
    }

    /**
     * Remove a previously registered listener.
     */
    fun removeListener(listener: (List<FileDiagnostics>) -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners(diagnostics: List<FileDiagnostics>) {
        ApplicationManager.getApplication().invokeLater {
            listeners.forEach { it(diagnostics) }
        }
    }

    /**
     * Perform the actual diagnostic collection on a pooled thread with a read action.
     * Skips collection if the project is in dumb mode (indexing).
     */
    private fun doCollect(onComplete: (List<FileDiagnostics>) -> Unit) {
        if (DumbService.getInstance(project).isDumb) {
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
     * Walk all content source roots and collect diagnostics from each supported source file.
     * Uses [DocumentMarkupModel] highlighters for detailed diagnostics and
     * [WolfTheProblemSolver] as a fallback for files marked as problematic.
     */
    private fun collectAllDiagnostics(): List<FileDiagnostics> {
        val psiManager = PsiManager.getInstance(project)
        val fileDocumentManager = FileDocumentManager.getInstance()
        val wolf = WolfTheProblemSolver.getInstance(project)
        val diagnosticsList = mutableListOf<FileDiagnostics>()

        val sourceRoots = ProjectRootManager.getInstance(project).contentSourceRoots

        sourceRoots.forEach { root ->
            VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Unit>() {
                override fun visitFile(file: VirtualFile): Boolean {
                    if (!file.isDirectory && isSourceFile(file)) {
                        collectFileDiagnostics(file, psiManager, fileDocumentManager, wolf)?.let {
                            diagnosticsList.add(it)
                        }
                    }
                    return true
                }
            })
        }

        return diagnosticsList.sortedBy { it.filePath }
    }

    /**
     * Collect diagnostics for a single file.
     *
     * 1. Try to extract [HighlightInfo] from the file's [DocumentMarkupModel].
     * 2. If no highlights found but [WolfTheProblemSolver] marks the file as problematic,
     *    create a generic ERROR diagnostic.
     * 3. Return `null` if the file has no diagnostics at all.
     */
    private fun collectFileDiagnostics(
        file: VirtualFile,
        psiManager: PsiManager,
        fileDocumentManager: FileDocumentManager,
        wolf: WolfTheProblemSolver
    ): FileDiagnostics? {
        psiManager.findFile(file) ?: return null
        val document = fileDocumentManager.getDocument(file) ?: return null

        val items = try {
            val markupModel = DocumentMarkupModel.forDocument(document, project, false)
            markupModel?.allHighlighters
                ?.mapNotNull { highlighter ->
                    val info = HighlightInfo.fromRangeHighlighter(highlighter)
                    if (info != null && info.severity >= HighlightSeverity.WARNING) {
                        convertToItem(file, document, info)
                    } else null
                }
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        if (items.isNotEmpty()) {
            return FileDiagnostics(filePath = file.path, items = items)
        }

        // Fallback: if WolfTheProblemSolver marks this file as problematic but
        // no highlights were found, create a generic error entry.
        if (wolf.isProblemFile(file)) {
            val item = DiagnosticItem(
                filePath = file.path,
                lineNumber = 1,
                message = "File has compilation problems (check Problems panel for details)",
                severity = DiagnosticSeverity.ERROR
            )
            return FileDiagnostics(filePath = file.path, items = listOf(item))
        }

        return null
    }

    /**
     * Check whether a file is a supported source file based on its extension.
     */
    private fun isSourceFile(file: VirtualFile): Boolean {
        val extension = file.extension?.lowercase() ?: return false
        return extension in SUPPORTED_EXTENSIONS
    }

    /**
     * Convert a [HighlightInfo] into a [DiagnosticItem].
     * Returns `null` if the highlight has no description.
     */
    private fun convertToItem(
        file: VirtualFile,
        document: Document,
        highlight: HighlightInfo
    ): DiagnosticItem? {
        val message = highlight.description ?: return null
        val lineNumber = document.getLineNumber(highlight.startOffset) + 1
        val severity = when {
            highlight.severity >= HighlightSeverity.ERROR -> DiagnosticSeverity.ERROR
            else -> DiagnosticSeverity.WARNING
        }
        return DiagnosticItem(
            filePath = file.path,
            lineNumber = lineNumber,
            message = message,
            severity = severity
        )
    }

    override fun dispose() {
        listeners.clear()
    }
}

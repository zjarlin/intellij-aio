package site.addzero.diagnostic.service

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
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
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.Alarm
import site.addzero.diagnostic.model.DiagnosticItem
import site.addzero.diagnostic.model.DiagnosticSeverity
import site.addzero.diagnostic.model.FileDiagnostics
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.PROJECT)
class DiagnosticCollectorService(private val project: Project) : Disposable {

    private val listeners = CopyOnWriteArrayList<(List<FileDiagnostics>) -> Unit>()
    private val debounceAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
    
    @Volatile
    private var latestDiagnostics: List<FileDiagnostics> = emptyList()

    companion object {
        fun getInstance(project: Project): DiagnosticCollectorService =
            project.getService(DiagnosticCollectorService::class.java)
    }

    init {
        setupListeners()
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
        
        return diagnosticsList.sortedBy { it.file.path }
    }
    
    private fun collectFileDiagnostics(
        file: VirtualFile,
        psiManager: PsiManager,
        fileDocumentManager: FileDocumentManager,
        wolf: WolfTheProblemSolver
    ): FileDiagnostics? {
        val psiFile = psiManager.findFile(file) ?: return null
        val document = fileDocumentManager.getDocument(file) ?: return null
        
        val highlights = try {
            DaemonCodeAnalyzerImpl.getHighlights(document, HighlightSeverity.WARNING, project)
        } catch (e: Exception) {
            emptyList()
        }
        
        val items = highlights
            .filter { it.severity >= HighlightSeverity.WARNING }
            .mapNotNull { highlight -> convertToItem(file, psiFile, document, highlight) }
        
        if (items.isNotEmpty()) {
            return FileDiagnostics(file, psiFile, items)
        }
        
        // 如果没有 highlights 但 wolf 标记为问题文件，也记录
        if (wolf.isProblemFile(file)) {
            val item = DiagnosticItem(
                file = file,
                psiFile = psiFile,
                lineNumber = 1,
                message = "文件存在编译问题（详情请查看 Problems 面板）",
                severity = DiagnosticSeverity.ERROR
            )
            return FileDiagnostics(file, psiFile, listOf(item))
        }
        
        return null
    }

    private fun isSourceFile(file: VirtualFile): Boolean {
        val extension = file.extension?.lowercase() ?: return false
        return extension in setOf("java", "kt", "kts", "groovy", "scala")
    }

    private fun convertToItem(
        file: VirtualFile,
        psiFile: PsiFile,
        document: Document,
        highlight: HighlightInfo
    ): DiagnosticItem? {
        val message = highlight.description ?: return null
        val lineNumber = document.getLineNumber(highlight.startOffset) + 1
        val severity = when {
            highlight.severity >= HighlightSeverity.ERROR -> DiagnosticSeverity.ERROR
            else -> DiagnosticSeverity.WARNING
        }
        return DiagnosticItem(file, psiFile, lineNumber, message, severity)
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
}

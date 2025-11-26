package site.addzero.diagnostic.service

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import site.addzero.diagnostic.model.DiagnosticItem
import site.addzero.diagnostic.model.DiagnosticSeverity
import site.addzero.diagnostic.model.FileDiagnostics
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class DiagnosticCollectorService(private val project: Project) {
    
    private val cachedDiagnostics = ConcurrentHashMap<String, FileDiagnostics>()
    
    companion object {
        fun getInstance(project: Project): DiagnosticCollectorService =
            project.getService(DiagnosticCollectorService::class.java)
    }
    
    fun collectDiagnostics(onComplete: (List<FileDiagnostics>) -> Unit) {
        if (DumbService.getInstance(project).isDumb) {
            onComplete(emptyList())
            return
        }
        
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = ReadAction.compute<List<FileDiagnostics>, Throwable> {
                collectAllDiagnostics()
            }
            ApplicationManager.getApplication().invokeLater {
                onComplete(result)
            }
        }
    }
    
    private fun collectAllDiagnostics(): List<FileDiagnostics> {
        val diagnosticsList = mutableListOf<FileDiagnostics>()
        val psiManager = PsiManager.getInstance(project)
        val documentManager = PsiDocumentManager.getInstance(project)
        val fileDocumentManager = FileDocumentManager.getInstance()
        
        val sourceRoots = ProjectRootManager.getInstance(project).contentSourceRoots
        
        sourceRoots.forEach { root ->
            VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Unit>() {
                override fun visitFile(file: VirtualFile): Boolean {
                    if (!file.isDirectory && isSourceFile(file)) {
                        val psiFile = psiManager.findFile(file)
                        if (psiFile != null) {
                            val document = fileDocumentManager.getDocument(file)
                            if (document != null) {
                                val highlights = DaemonCodeAnalyzerImpl.getHighlights(
                                    document, 
                                    HighlightSeverity.WARNING, 
                                    project
                                )
                                
                                val items = highlights
                                    .filter { it.severity >= HighlightSeverity.WARNING }
                                    .mapNotNull { highlight -> 
                                        convertToItem(file, psiFile, document, highlight) 
                                    }
                                
                                if (items.isNotEmpty()) {
                                    diagnosticsList.add(FileDiagnostics(file, psiFile, items))
                                }
                            }
                        }
                    }
                    return true
                }
            })
        }
        
        return diagnosticsList.sortedBy { it.file.path }
    }
    
    private fun isSourceFile(file: VirtualFile): Boolean {
        val extension = file.extension?.lowercase() ?: return false
        return extension in setOf("java", "kt", "kts", "groovy", "scala", "xml", "json", "yaml", "yml", "properties")
    }
    
    private fun convertToItem(
        file: VirtualFile,
        psiFile: com.intellij.psi.PsiFile,
        document: com.intellij.openapi.editor.Document,
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

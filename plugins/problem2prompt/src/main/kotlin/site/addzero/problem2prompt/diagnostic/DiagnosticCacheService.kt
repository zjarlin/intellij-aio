package site.addzero.problem2prompt.diagnostic

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import site.addzero.problem2prompt.diagnostic.model.DiagnosticStatistics
import site.addzero.problem2prompt.diagnostic.model.FileDiagnostics
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Project-level diagnostic cache service.
 *
 * Caches all file diagnostics in a thread-safe [ConcurrentHashMap] keyed by file path strings,
 * providing fast query interfaces for MCP Tool handlers.
 *
 * Design extracted from problem4ai's [GlobalDiagnosticCache], adapted for problem2prompt:
 * - Uses `String` file paths as cache keys (not `VirtualFile`)
 * - Delegates collection to [DiagnosticCollectorService]
 * - Listens for incremental updates via [DiagnosticCollectorService.addListener]
 * - Eager initialization via startup activity calling [performFullScan]
 *
 * Thread safety:
 * - [ConcurrentHashMap] for lock-free reads and safe concurrent writes
 * - [AtomicBoolean] for initialization tracking
 *
 * @see DiagnosticCollectorService
 */
@Service(Service.Level.PROJECT)
class DiagnosticCacheService(private val project: Project) : Disposable {

    private val log = logger<DiagnosticCacheService>()

    /** Diagnostic cache: filePath → FileDiagnostics */
    private val diagnosticsCache = ConcurrentHashMap<String, FileDiagnostics>()

    /** Whether the initial full scan has completed. */
    private val initialized = AtomicBoolean(false)

    /** Listener reference for cleanup on dispose. */
    private val incrementalUpdateListener: (List<FileDiagnostics>) -> Unit = { diagnostics ->
        updateCache(diagnostics)
    }

    companion object {
        fun getInstance(project: Project): DiagnosticCacheService =
            project.getService(DiagnosticCacheService::class.java)
    }

    init {
        // Subscribe to DiagnosticCollectorService for incremental updates
        DiagnosticCollectorService.getInstance(project).addListener(incrementalUpdateListener)
    }

    /**
     * Perform a full diagnostic scan (eager initialization).
     *
     * If the project is currently in dumb mode (indexing), the scan is deferred
     * until smart mode via [DumbService.runWhenSmart].
     */
    fun performFullScan() {
        if (DumbService.getInstance(project).isDumb) {
            log.info("Project is indexing, deferring full diagnostic scan until smart mode")
            DumbService.getInstance(project).runWhenSmart {
                doFullScan()
            }
        } else {
            doFullScan()
        }
    }

    private fun doFullScan() {
        log.info("Starting full diagnostic scan")
        val collector = DiagnosticCollectorService.getInstance(project)
        collector.collectDiagnostics { diagnostics ->
            diagnosticsCache.clear()
            diagnostics.forEach { fileDiag ->
                diagnosticsCache[fileDiag.filePath] = fileDiag
            }
            initialized.set(true)
            log.info("Full diagnostic scan completed: ${diagnosticsCache.size} files with diagnostics")
        }
    }

    /**
     * Update the cache with incremental diagnostic results.
     *
     * Retains only files that are still present in the new diagnostics list,
     * removing entries for files that no longer have diagnostics.
     */
    private fun updateCache(diagnostics: List<FileDiagnostics>) {
        // Determine the set of files that currently have diagnostics
        val currentFiles = diagnostics.map { it.filePath }.toSet()

        // Remove files that no longer have diagnostics
        diagnosticsCache.keys.retainAll(currentFiles)

        // Update or add files with diagnostics
        diagnostics.forEach { fileDiag ->
            diagnosticsCache[fileDiag.filePath] = fileDiag
        }

        log.debug("Cache updated: ${diagnosticsCache.size} files with diagnostics")
    }

    /**
     * Get diagnostics for a specific file.
     *
     * @param filePath the absolute or project-relative file path
     * @return the [FileDiagnostics] for the file, or `null` if no diagnostics exist
     */
    fun getDiagnostics(filePath: String): FileDiagnostics? {
        return diagnosticsCache[filePath]
    }

    /**
     * Get all cached diagnostics across the project.
     *
     * @return a snapshot list of all [FileDiagnostics] currently in the cache
     */
    fun getAllDiagnostics(): List<FileDiagnostics> {
        return diagnosticsCache.values.toList()
    }

    /**
     * Get all files that have at least one ERROR-severity diagnostic.
     *
     * @return a list of [FileDiagnostics] for files with errors
     */
    fun getErrorFiles(): List<FileDiagnostics> {
        return diagnosticsCache.values.filter { it.hasErrors }.toList()
    }

    /**
     * Compute aggregate statistics from the current cache state.
     *
     * @return a [DiagnosticStatistics] snapshot
     */
    fun getStatistics(): DiagnosticStatistics {
        val allFiles = diagnosticsCache.values.toList()
        val totalErrors = allFiles.sumOf { it.errorCount }
        val totalWarnings = allFiles.sumOf { it.warningCount }

        return DiagnosticStatistics(
            totalFiles = allFiles.size,
            errorFiles = allFiles.count { it.hasErrors },
            warningFiles = allFiles.count { it.hasWarnings },
            totalErrors = totalErrors,
            totalWarnings = totalWarnings
        )
    }

    /**
     * Check whether a specific file has any ERROR-severity diagnostics.
     *
     * @param filePath the absolute or project-relative file path
     * @return `true` if the file has at least one error
     */
    fun hasErrors(filePath: String): Boolean {
        return diagnosticsCache[filePath]?.hasErrors ?: false
    }

    /**
     * Whether the initial full scan has completed.
     *
     * @return `true` if [performFullScan] has finished at least once
     */
    fun isInitialized(): Boolean = initialized.get()

    override fun dispose() {
        // Remove our listener from the collector service
        try {
            DiagnosticCollectorService.getInstance(project).removeListener(incrementalUpdateListener)
        } catch (_: Exception) {
            // Project may already be disposed
        }
        diagnosticsCache.clear()
        log.info("DiagnosticCacheService disposed")
    }
}

package site.addzero.problem2prompt.diagnostic.model

/**
 * Severity level for a diagnostic item.
 *
 * Simplified from problem4ai's design — uses a plain enum instead of
 * mapping to HighlightSeverity, keeping the model independent of the IDE API.
 */
enum class DiagnosticSeverity {
    ERROR,
    WARNING
}

/**
 * A single diagnostic entry for a specific location in a file.
 *
 * Key difference from problem4ai: uses [filePath] (String) instead of VirtualFile/PsiFile,
 * making this model independent of the IDE's virtual file system and suitable for MCP serialization.
 *
 * @property filePath  Absolute or project-relative path to the source file
 * @property lineNumber  1-based line number where the diagnostic occurs
 * @property message  Human-readable description of the diagnostic
 * @property severity  Severity level (ERROR or WARNING)
 */
data class DiagnosticItem(
    val filePath: String,
    val lineNumber: Int,
    val message: String,
    val severity: DiagnosticSeverity
)

/**
 * All diagnostics for a single file, grouped together.
 *
 * @property filePath  Absolute or project-relative path to the source file
 * @property items  List of diagnostic entries for this file
 */
data class FileDiagnostics(
    val filePath: String,
    val items: List<DiagnosticItem>
) {
    /** `true` if this file contains at least one ERROR-severity diagnostic. */
    val hasErrors: Boolean
        get() = items.any { it.severity == DiagnosticSeverity.ERROR }

    /** `true` if this file contains at least one WARNING-severity diagnostic. */
    val hasWarnings: Boolean
        get() = items.any { it.severity == DiagnosticSeverity.WARNING }

    /** Number of ERROR-severity diagnostics in this file. */
    val errorCount: Int
        get() = items.count { it.severity == DiagnosticSeverity.ERROR }

    /** Number of WARNING-severity diagnostics in this file. */
    val warningCount: Int
        get() = items.count { it.severity == DiagnosticSeverity.WARNING }
}

/**
 * Aggregate statistics across all files in the diagnostic cache.
 *
 * @property totalFiles  Number of files that have at least one diagnostic
 * @property errorFiles  Number of files that have at least one ERROR diagnostic
 * @property warningFiles  Number of files that have at least one WARNING diagnostic
 * @property totalErrors  Total count of ERROR diagnostics across all files
 * @property totalWarnings  Total count of WARNING diagnostics across all files
 */
data class DiagnosticStatistics(
    val totalFiles: Int,
    val errorFiles: Int,
    val warningFiles: Int,
    val totalErrors: Int,
    val totalWarnings: Int
)

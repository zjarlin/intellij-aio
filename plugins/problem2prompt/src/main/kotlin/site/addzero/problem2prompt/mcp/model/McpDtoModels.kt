package site.addzero.problem2prompt.mcp.model

import kotlinx.serialization.Serializable

/**
 * DTO for a single diagnostic entry, used in MCP Tool responses.
 *
 * @property filePath   Absolute or project-relative path to the source file
 * @property lineNumber 1-based line number where the diagnostic occurs
 * @property message    Human-readable description of the diagnostic
 * @property severity   Severity level: "ERROR" or "WARNING"
 */
@Serializable
data class DiagnosticItemDto(
    val filePath: String,
    val lineNumber: Int,
    val message: String,
    val severity: String // "ERROR" or "WARNING"
)

/**
 * DTO for all diagnostics of a single file, used in MCP Tool responses.
 *
 * @property filePath    Absolute or project-relative path to the source file
 * @property diagnostics List of diagnostic entries for this file
 */
@Serializable
data class FileDiagnosticsDto(
    val filePath: String,
    val diagnostics: List<DiagnosticItemDto>
)

/**
 * DTO for aggregate diagnostic statistics across the project.
 *
 * @property totalFiles     Number of files that have at least one diagnostic
 * @property errorFiles     Number of files that have at least one ERROR diagnostic
 * @property warningFiles   Number of files that have at least one WARNING diagnostic
 * @property totalErrors    Total count of ERROR diagnostics across all files
 * @property totalWarnings  Total count of WARNING diagnostics across all files
 */
@Serializable
data class DiagnosticStatisticsDto(
    val totalFiles: Int,
    val errorFiles: Int,
    val warningFiles: Int,
    val totalErrors: Int,
    val totalWarnings: Int
)

/**
 * DTO summarising error count for a single file, used by `get_files_with_errors`.
 *
 * @property filePath   Absolute or project-relative path to the source file
 * @property errorCount Number of ERROR-severity diagnostics in this file
 */
@Serializable
data class FileErrorSummaryDto(
    val filePath: String,
    val errorCount: Int
)

/**
 * DTO for the overall compilation status of the project.
 *
 * @property compilesCleanly `true` if there are zero ERROR-severity diagnostics
 * @property totalErrors     Total count of ERROR diagnostics across all files
 * @property totalWarnings   Total count of WARNING diagnostics across all files
 */
@Serializable
data class CompilationStatusDto(
    val compilesCleanly: Boolean,
    val totalErrors: Int,
    val totalWarnings: Int
)

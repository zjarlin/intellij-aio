package site.addzero.problem2prompt.mcp.model

import site.addzero.problem2prompt.diagnostic.model.DiagnosticItem
import site.addzero.problem2prompt.diagnostic.model.DiagnosticStatistics
import site.addzero.problem2prompt.diagnostic.model.FileDiagnostics

/**
 * Extension functions for converting internal diagnostic models to MCP DTO models.
 *
 * These conversions are used by [site.addzero.problem2prompt.mcp.McpToolService]
 * to serialize diagnostic data into MCP-protocol-compliant JSON responses.
 */

/**
 * Convert a [DiagnosticItem] to its DTO representation.
 *
 * The [DiagnosticItem.severity] enum is converted to its string name
 * (e.g. `DiagnosticSeverity.ERROR` → `"ERROR"`).
 */
fun DiagnosticItem.toDto(): DiagnosticItemDto = DiagnosticItemDto(
    filePath = filePath,
    lineNumber = lineNumber,
    message = message,
    severity = severity.name
)

/**
 * Convert a [FileDiagnostics] to its DTO representation.
 *
 * Each contained [DiagnosticItem] is mapped to [DiagnosticItemDto] via [DiagnosticItem.toDto].
 */
fun FileDiagnostics.toDto(): FileDiagnosticsDto = FileDiagnosticsDto(
    filePath = filePath,
    diagnostics = items.map { it.toDto() }
)

/**
 * Convert a [DiagnosticStatistics] to its DTO representation.
 *
 * Direct field mapping — all fields have identical names and types.
 */
fun DiagnosticStatistics.toDto(): DiagnosticStatisticsDto = DiagnosticStatisticsDto(
    totalFiles = totalFiles,
    errorFiles = errorFiles,
    warningFiles = warningFiles,
    totalErrors = totalErrors,
    totalWarnings = totalWarnings
)

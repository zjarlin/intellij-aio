package site.addzero.problem2prompt.mcp

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import site.addzero.problem2prompt.diagnostic.DiagnosticCacheService
import site.addzero.problem2prompt.diagnostic.model.DiagnosticSeverity
import site.addzero.problem2prompt.mcp.model.*

/**
 * Encapsulates all MCP Tool registration and handler logic.
 *
 * This is a plain class (NOT a @Service) — it is instantiated by [McpServerLifecycleService]
 * and receives the [Project] reference for accessing project-level services.
 *
 * Each handler method queries [DiagnosticCacheService], converts internal models to DTOs
 * via extension functions in [site.addzero.problem2prompt.mcp.model], serializes to JSON,
 * and wraps the result in a [CallToolResult] with [TextContent].
 *
 * @see DiagnosticCacheService
 */
class McpToolService(private val project: Project) {

    private val log = logger<McpToolService>()

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    private val cache: DiagnosticCacheService
        get() = DiagnosticCacheService.getInstance(project)

    // ──────────────────────────────────────────────
    // Tool Registration
    // ──────────────────────────────────────────────

    /**
     * Register all 5 MCP Tools on the given [mcpServer].
     *
     * Tools registered:
     * - `get_file_diagnostics` — diagnostics for a single file
     * - `get_project_diagnostics` — all diagnostics, optionally filtered by severity
     * - `get_diagnostic_statistics` — aggregate statistics
     * - `get_files_with_errors` — files that have ERROR-severity diagnostics
     * - `get_compilation_status` — boolean compilation health + counts
     */
    fun registerTools(mcpServer: Server) {
        log.info("Registering MCP diagnostic tools")

        // 1. get_file_diagnostics
        mcpServer.addTool(
            name = "get_file_diagnostics",
            description = "Get all diagnostics (errors and warnings) for a specific file in the project.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("filePath", buildJsonObject {
                        put("type", "string")
                        put("description", "Absolute or project-relative path to the source file")
                    })
                },
                required = listOf("filePath")
            )
        ) { request ->
            val filePath = request.arguments?.get("filePath")?.jsonPrimitive?.content ?: ""
            handleGetFileDiagnostics(filePath)
        }

        // 2. get_project_diagnostics
        mcpServer.addTool(
            name = "get_project_diagnostics",
            description = "Get all diagnostics across the entire project, optionally filtered by severity. Results are grouped by file.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("severity", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional severity filter: \"ERROR\" or \"WARNING\". If omitted, returns all diagnostics.")
                        put("enum", buildJsonObject {
                            // Note: enum is typically an array, but ToolSchema properties is a JsonObject.
                            // We describe it in the description instead for clarity.
                        })
                    })
                }
            )
        ) { request ->
            val severity = request.arguments?.get("severity")?.jsonPrimitive?.content
            handleGetProjectDiagnostics(severity)
        }

        // 3. get_diagnostic_statistics
        mcpServer.addTool(
            name = "get_diagnostic_statistics",
            description = "Get aggregate diagnostic statistics for the project: total files, error/warning file counts, and total error/warning counts.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {}
            )
        ) { request ->
            handleGetDiagnosticStatistics()
        }

        // 4. get_files_with_errors
        mcpServer.addTool(
            name = "get_files_with_errors",
            description = "Get a list of all files that have at least one compilation error, with error counts per file.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {}
            )
        ) { request ->
            handleGetFilesWithErrors()
        }

        // 5. get_compilation_status
        mcpServer.addTool(
            name = "get_compilation_status",
            description = "Check whether the project compiles cleanly (zero errors). Returns a boolean status along with total error and warning counts.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {}
            )
        ) { request ->
            handleGetCompilationStatus()
        }

        log.info("All 5 MCP diagnostic tools registered successfully")
    }

    // ──────────────────────────────────────────────
    // Tool Handlers
    // ──────────────────────────────────────────────

    /**
     * Handle `get_file_diagnostics` tool call.
     *
     * Queries [DiagnosticCacheService.getDiagnostics] for the given [filePath],
     * converts to [FileDiagnosticsDto], and returns as JSON [TextContent].
     *
     * Error cases:
     * - Cache not initialized → empty result with warning
     * - File not found in cache → error response with "file not found" message
     */
    internal fun handleGetFileDiagnostics(filePath: String): CallToolResult {
        if (!cache.isInitialized()) {
            log.warn("Cache not initialized when querying file diagnostics for: $filePath")
            val emptyDto = FileDiagnosticsDto(filePath = filePath, diagnostics = emptyList())
            return CallToolResult(
                content = listOf(TextContent(json.encodeToString(emptyDto))),
                isError = false
            )
        }

        val fileDiagnostics = cache.getDiagnostics(filePath)
        if (fileDiagnostics == null) {
            return CallToolResult(
                content = listOf(TextContent("File not found in diagnostics cache: $filePath")),
                isError = true
            )
        }

        val dto = fileDiagnostics.toDto()
        return CallToolResult(
            content = listOf(TextContent(json.encodeToString(dto)))
        )
    }

    /**
     * Handle `get_project_diagnostics` tool call.
     *
     * Queries [DiagnosticCacheService.getAllDiagnostics], optionally filters by [severityFilter],
     * converts each to [FileDiagnosticsDto], and returns as JSON [TextContent].
     *
     * If [severityFilter] is provided but not a valid severity ("ERROR"/"WARNING"),
     * the filter is ignored and all diagnostics are returned.
     */
    internal fun handleGetProjectDiagnostics(severityFilter: String?): CallToolResult {
        if (!cache.isInitialized()) {
            log.warn("Cache not initialized when querying project diagnostics")
            return CallToolResult(
                content = listOf(TextContent(json.encodeToString(emptyList<FileDiagnosticsDto>())))
            )
        }

        val allDiagnostics = cache.getAllDiagnostics()

        // Parse severity filter; ignore if invalid
        val parsedSeverity = severityFilter?.let {
            try {
                DiagnosticSeverity.valueOf(it.uppercase())
            } catch (_: IllegalArgumentException) {
                log.warn("Invalid severity filter '$it', returning all diagnostics")
                null
            }
        }

        val dtos = if (parsedSeverity != null) {
            // Filter items within each file, then exclude files with no remaining items
            allDiagnostics.mapNotNull { fileDiag ->
                val filteredItems = fileDiag.items.filter { it.severity == parsedSeverity }
                if (filteredItems.isEmpty()) null
                else FileDiagnosticsDto(
                    filePath = fileDiag.filePath,
                    diagnostics = filteredItems.map { it.toDto() }
                )
            }
        } else {
            allDiagnostics.map { it.toDto() }
        }

        return CallToolResult(
            content = listOf(TextContent(json.encodeToString(dtos)))
        )
    }

    /**
     * Handle `get_diagnostic_statistics` tool call.
     *
     * Delegates to [DiagnosticCacheService.getStatistics], converts to
     * [DiagnosticStatisticsDto], and returns as JSON [TextContent].
     */
    internal fun handleGetDiagnosticStatistics(): CallToolResult {
        if (!cache.isInitialized()) {
            log.warn("Cache not initialized when querying diagnostic statistics")
            val emptyStats = DiagnosticStatisticsDto(
                totalFiles = 0, errorFiles = 0, warningFiles = 0,
                totalErrors = 0, totalWarnings = 0
            )
            return CallToolResult(
                content = listOf(TextContent(json.encodeToString(emptyStats)))
            )
        }

        val stats = cache.getStatistics().toDto()
        return CallToolResult(
            content = listOf(TextContent(json.encodeToString(stats)))
        )
    }

    /**
     * Handle `get_files_with_errors` tool call.
     *
     * Queries [DiagnosticCacheService.getErrorFiles], maps each to
     * [FileErrorSummaryDto] (filePath + errorCount), and returns as JSON [TextContent].
     */
    internal fun handleGetFilesWithErrors(): CallToolResult {
        if (!cache.isInitialized()) {
            log.warn("Cache not initialized when querying files with errors")
            return CallToolResult(
                content = listOf(TextContent(json.encodeToString(emptyList<FileErrorSummaryDto>())))
            )
        }

        val errorFiles = cache.getErrorFiles()
        val dtos = errorFiles.map { fileDiag ->
            FileErrorSummaryDto(
                filePath = fileDiag.filePath,
                errorCount = fileDiag.errorCount
            )
        }

        return CallToolResult(
            content = listOf(TextContent(json.encodeToString(dtos)))
        )
    }

    /**
     * Handle `get_compilation_status` tool call.
     *
     * Computes compilation status from [DiagnosticCacheService.getStatistics]:
     * - `compilesCleanly` is `true` iff `totalErrors == 0`
     * - Includes `totalErrors` and `totalWarnings` counts
     */
    internal fun handleGetCompilationStatus(): CallToolResult {
        if (!cache.isInitialized()) {
            log.warn("Cache not initialized when querying compilation status")
            val emptyStatus = CompilationStatusDto(
                compilesCleanly = true,
                totalErrors = 0,
                totalWarnings = 0
            )
            return CallToolResult(
                content = listOf(TextContent(json.encodeToString(emptyStatus)))
            )
        }

        val stats = cache.getStatistics()
        val dto = CompilationStatusDto(
            compilesCleanly = stats.totalErrors == 0,
            totalErrors = stats.totalErrors,
            totalWarnings = stats.totalWarnings
        )

        return CallToolResult(
            content = listOf(TextContent(json.encodeToString(dto)))
        )
    }
}

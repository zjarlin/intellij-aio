package site.addzero.diagnostic.mcp

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.*
import site.addzero.diagnostic.model.DiagnosticSeverity
import site.addzero.diagnostic.service.DiagnosticCollectorService
import java.io.OutputStream
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

/**
 * Problem4AI MCP 服务器
 *
 * 实现 Model Context Protocol 协议，暴露诊断功能给 AI
 */
@Service(Service.Level.PROJECT)
class DiagnosticMcpServer(private val project: Project) {

    private var server: HttpServer? = null
    private var port: Int = 0

    companion object {
        private const val DEFAULT_PORT = 8964
        private var nextPort = DEFAULT_PORT

        fun getInstance(project: Project): DiagnosticMcpServer =
            project.getService(DiagnosticMcpServer::class.java)
    }

    init {
        startServer()
    }

    private fun startServer() {
        try {
            port = findAvailablePort()
            server = HttpServer.create(InetSocketAddress(port), 0).apply {
                // MCP Protocol endpoints
                createContext("/mcp/v1/initialize", InitializeHandler())
                createContext("/mcp/v1/tools/list", ToolsListHandler())
                createContext("/mcp/v1/tools/call", ToolsCallHandler())
                createContext("/mcp/v1/resources/list", ResourcesListHandler())
                createContext("/mcp/v1/resources/read", ResourcesReadHandler())
                createContext("/mcp/v1/prompts/list", PromptsListHandler())
                createContext("/mcp/v1/prompts/get", PromptsGetHandler())

                // Legacy simple endpoints
                createContext("/health", HealthHandler())
                createContext("/stats", StatsHandler())
                createContext("/diagnostics", DiagnosticsHandler())

                executor = java.util.concurrent.Executors.newFixedThreadPool(2)
                start()
            }
            println("Problem4AI MCP Server started at http://localhost:$port")
        } catch (e: Exception) {
            println("Failed to start MCP server: ${e.message}")
        }
    }

    private fun findAvailablePort(): Int {
        var port = nextPort
        while (true) {
            try {
                java.net.ServerSocket(port).close()
                nextPort = port + 1
                return port
            } catch (e: Exception) {
                port++
            }
        }
    }

    private fun sendJson(exchange: HttpExchange, json: String, statusCode: Int = 200) {
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json; charset=UTF-8")
        exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
        exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
        exchange.responseBody.use { os: OutputStream ->
            os.write(bytes)
        }
    }

    private fun readRequestBody(exchange: HttpExchange): String {
        return exchange.requestBody.use { it.readBytes().toString(StandardCharsets.UTF_8) }
    }

    // ========== MCP Protocol Handlers ==========

    inner class InitializeHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val response = buildJsonObject {
                put("protocolVersion", "2024-11-05")
                put("serverInfo", buildJsonObject {
                    put("name", "problem4ai")
                    put("version", "1.0.0")
                })
                put("capabilities", buildJsonObject {
                    put("tools", buildJsonObject { })
                    put("resources", buildJsonObject { })
                    put("prompts", buildJsonObject { })
                })
            }
            sendJson(exchange, response.toString())
        }
    }

    inner class ToolsListHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val response = buildJsonObject {
                put("tools", buildJsonArray {
                    // Tool 1: get_project_stats
                    add(buildJsonObject {
                        put("name", "get_project_stats")
                        put("description", "获取项目诊断统计信息，包括错误和警告的总数")
                        put("inputSchema", buildJsonObject {
                            put("type", "object")
                            put("properties", buildJsonObject { })
                        })
                    })
                    // Tool 2: get_all_errors
                    add(buildJsonObject {
                        put("name", "get_all_errors")
                        put("description", "获取项目中所有包含错误的文件列表，包括文件路径、行号和错误信息")
                        put("inputSchema", buildJsonObject {
                            put("type", "object")
                            put("properties", buildJsonObject { })
                        })
                    })
                    // Tool 3: get_all_warnings
                    add(buildJsonObject {
                        put("name", "get_all_warnings")
                        put("description", "获取项目中所有警告信息")
                        put("inputSchema", buildJsonObject {
                            put("type", "object")
                            put("properties", buildJsonObject { })
                        })
                    })
                    // Tool 4: get_file_diagnostics
                    add(buildJsonObject {
                        put("name", "get_file_diagnostics")
                        put("description", "获取指定文件的诊断信息，需要提供文件的相对路径或文件名")
                        put("inputSchema", buildJsonObject {
                            put("type", "object")
                            put("properties", buildJsonObject {
                                put("filePath", buildJsonObject {
                                    put("type", "string")
                                    put("description", "文件路径或文件名")
                                })
                            })
                            put("required", buildJsonArray { add("filePath") })
                        })
                    })
                    // Tool 5: refresh_diagnostics
                    add(buildJsonObject {
                        put("name", "refresh_diagnostics")
                        put("description", "触发重新扫描项目诊断信息，返回扫描结果")
                        put("inputSchema", buildJsonObject {
                            put("type", "object")
                            put("properties", buildJsonObject { })
                        })
                    })
                    // Tool 6: generate_fix_prompt
                    add(buildJsonObject {
                        put("name", "generate_fix_prompt")
                        put("description", "为指定文件生成AI修复提示词，包含完整的错误信息和代码上下文")
                        put("inputSchema", buildJsonObject {
                            put("type", "object")
                            put("properties", buildJsonObject {
                                put("filePath", buildJsonObject {
                                    put("type", "string")
                                    put("description", "文件路径或文件名")
                                })
                            })
                            put("required", buildJsonArray { add("filePath") })
                        })
                    })
                })
            }
            sendJson(exchange, response.toString())
        }
    }

    inner class ToolsCallHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            if (exchange.requestMethod != "POST") {
                exchange.sendResponseHeaders(405, -1)
                return
            }

            val body = readRequestBody(exchange)
            val request = Json.parseToJsonElement(body).jsonObject
            val toolName = request["name"]?.jsonPrimitive?.content
            val args = request["arguments"]?.jsonObject ?: buildJsonObject { }

            val result = when (toolName) {
                "get_project_stats" -> handleGetProjectStats()
                "get_all_errors" -> handleGetAllErrors()
                "get_all_warnings" -> handleGetAllWarnings()
                "get_file_diagnostics" -> handleGetFileDiagnostics(args)
                "refresh_diagnostics" -> handleRefreshDiagnostics()
                "generate_fix_prompt" -> handleGenerateFixPrompt(args)
                else -> buildJsonObject {
                    put("error", "Unknown tool: $toolName")
                }
            }

            val response = buildJsonObject {
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", result.toString())
                    })
                })
            }
            sendJson(exchange, response.toString())
        }
    }

    inner class ResourcesListHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val response = buildJsonObject {
                put("resources", buildJsonArray {
                    add(buildJsonObject {
                        put("uri", "diagnostics://project/overview")
                        put("name", "项目诊断总览")
                        put("description", "包含项目所有诊断信息的JSON数据")
                        put("mimeType", "application/json")
                    })
                    add(buildJsonObject {
                        put("uri", "diagnostics://errors")
                        put("name", "错误列表")
                        put("description", "仅包含错误（不含警告）的JSON数据")
                        put("mimeType", "application/json")
                    })
                })
            }
            sendJson(exchange, response.toString())
        }
    }

    inner class ResourcesReadHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val uri = exchange.requestURI.query?.let { parseQuery(it)["uri"] } ?: ""

            val content = when (uri) {
                "diagnostics://project/overview" -> getDiagnosticsOverview()
                "diagnostics://errors" -> getErrorsOnly()
                else -> buildJsonObject { put("error", "Unknown resource: $uri") }
            }

            val response = buildJsonObject {
                put("contents", buildJsonArray {
                    add(buildJsonObject {
                        put("uri", uri)
                        put("mimeType", "application/json")
                        put("text", content.toString())
                    })
                })
            }
            sendJson(exchange, response.toString())
        }
    }

    inner class PromptsListHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val response = buildJsonObject {
                put("prompts", buildJsonArray {
                    add(buildJsonObject {
                        put("name", "fix_all_errors")
                        put("description", "生成修复项目中所有错误的提示词")
                    })
                    add(buildJsonObject {
                        put("name", "code_review")
                        put("description", "基于诊断信息生成代码审查提示词")
                    })
                })
            }
            sendJson(exchange, response.toString())
        }
    }

    inner class PromptsGetHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val query = parseQuery(exchange.requestURI.query ?: "")
            val promptName = query["name"] ?: ""

            val (description, messages) = when (promptName) {
                "fix_all_errors" -> getFixAllErrorsPrompt()
                "code_review" -> getCodeReviewPrompt()
                else -> Pair("Unknown prompt", buildJsonArray { })
            }

            val response = buildJsonObject {
                put("description", description)
                put("messages", messages)
            }
            sendJson(exchange, response.toString())
        }
    }

    // ========== Legacy Handlers ==========

    inner class HealthHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val json = """{"status": "ok", "port": $port}"""
            sendJson(exchange, json)
        }
    }

    inner class StatsHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val collector = DiagnosticCollectorService.getInstance(project)
            val diagnostics = collector.getAllProblemDiagnostics()
            val errorCount = diagnostics.sumOf { it.items.count { item -> item.severity == DiagnosticSeverity.ERROR } }
            val warningCount = diagnostics.sumOf { it.items.count { item -> item.severity == DiagnosticSeverity.WARNING } }

            val json = buildJsonObject {
                put("project", project.name)
                put("totalFiles", diagnostics.size)
                put("totalErrors", errorCount)
                put("totalWarnings", warningCount)
            }
            sendJson(exchange, json.toString())
        }
    }

    inner class DiagnosticsHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            sendJson(exchange, getDiagnosticsOverview().toString())
        }
    }

    // ========== Tool Implementations ==========

    private fun handleGetProjectStats(): JsonObject {
        val collector = DiagnosticCollectorService.getInstance(project)
        val diagnostics = collector.getAllProblemDiagnostics()
        val errorCount = diagnostics.sumOf { it.items.count { item -> item.severity == DiagnosticSeverity.ERROR } }
        val warningCount = diagnostics.sumOf { it.items.count { item -> item.severity == DiagnosticSeverity.WARNING } }

        return buildJsonObject {
            put("project", project.name)
            put("fileCount", diagnostics.size)
            put("errorCount", errorCount)
            put("warningCount", warningCount)
        }
    }

    private fun handleGetAllErrors(): JsonObject {
        val collector = DiagnosticCollectorService.getInstance(project)
        val errorFiles = collector.getAllProblemDiagnostics().filter { it.hasErrors }

        return buildJsonObject {
            put("count", errorFiles.sumOf { it.items.count { item -> item.severity == DiagnosticSeverity.ERROR } })
            put("files", buildJsonArray {
                errorFiles.forEach { fileDiag ->
                    add(buildJsonObject {
                        put("file", fileDiag.file.path)
                        put("errors", buildJsonArray {
                            fileDiag.items.filter { it.severity == DiagnosticSeverity.ERROR }.forEach { item ->
                                add(buildJsonObject {
                                    put("line", item.lineNumber)
                                    put("message", item.message)
                                })
                            }
                        })
                    })
                }
            })
        }
    }

    private fun handleGetAllWarnings(): JsonObject {
        val collector = DiagnosticCollectorService.getInstance(project)
        val warningFiles = collector.getAllProblemDiagnostics().filter { it.hasWarnings }

        return buildJsonObject {
            put("count", warningFiles.sumOf { it.items.count { item -> item.severity == DiagnosticSeverity.WARNING } })
            put("files", buildJsonArray {
                warningFiles.forEach { fileDiag ->
                    add(buildJsonObject {
                        put("file", fileDiag.file.path)
                        put("warnings", buildJsonArray {
                            fileDiag.items.filter { it.severity == DiagnosticSeverity.WARNING }.forEach { item ->
                                add(buildJsonObject {
                                    put("line", item.lineNumber)
                                    put("message", item.message)
                                })
                            }
                        })
                    })
                }
            })
        }
    }

    private fun handleGetFileDiagnostics(args: JsonObject): JsonObject {
        val filePath = args["filePath"]?.jsonPrimitive?.content ?: return buildJsonObject {
            put("error", "filePath is required")
        }

        val collector = DiagnosticCollectorService.getInstance(project)
        val fileDiag = collector.getAllProblemDiagnostics().find {
            it.file.path.contains(filePath) || it.file.name == filePath
        }

        return if (fileDiag == null) {
            buildJsonObject { put("error", "File not found: $filePath") }
        } else {
            buildJsonObject {
                put("file", fileDiag.file.path)
                put("items", buildJsonArray {
                    fileDiag.items.forEach { item ->
                        add(buildJsonObject {
                            put("line", item.lineNumber)
                            put("severity", if (item.severity == DiagnosticSeverity.ERROR) "error" else "warning")
                            put("message", item.message)
                        })
                    }
                })
            }
        }
    }

    private fun handleRefreshDiagnostics(): JsonObject {
        val collector = DiagnosticCollectorService.getInstance(project)
        // Note: This is async, returns current cached data
        collector.triggerIncrementalScan()

        val diagnostics = collector.getAllProblemDiagnostics()
        return buildJsonObject {
            put("status", "scan triggered")
            put("currentFileCount", diagnostics.size)
        }
    }

    private fun handleGenerateFixPrompt(args: JsonObject): JsonObject {
        val filePath = args["filePath"]?.jsonPrimitive?.content ?: return buildJsonObject {
            put("error", "filePath is required")
        }

        val collector = DiagnosticCollectorService.getInstance(project)
        val fileDiag = collector.getAllProblemDiagnostics().find {
            it.file.path.contains(filePath) || it.file.name == filePath
        }

        val prompt = if (fileDiag == null) {
            "File not found: $filePath"
        } else {
            buildString {
                appendLine("请帮我修复以下编译问题：")
                appendLine()
                appendLine("=== 文件: ${fileDiag.file.name} ===")
                appendLine()
                fileDiag.items.forEachIndexed { index, item ->
                    appendLine("问题 ${index + 1}:")
                    appendLine("  行号: ${item.lineNumber}")
                    appendLine("  类型: ${if (item.severity == DiagnosticSeverity.ERROR) "错误" else "警告"}")
                    appendLine("  内容: ${item.message}")
                    appendLine()
                }
                appendLine("请提供修复后的代码，并解释每个修改的原因。")
            }
        }

        return buildJsonObject {
            put("prompt", prompt)
        }
    }

    // ========== Resource Implementations ==========

    private fun getDiagnosticsOverview(): JsonObject {
        val collector = DiagnosticCollectorService.getInstance(project)
        val diagnostics = collector.getAllProblemDiagnostics()

        return buildJsonObject {
            put("project", project.name)
            put("fileCount", diagnostics.size)
            put("files", buildJsonArray {
                diagnostics.forEach { fileDiag ->
                    add(buildJsonObject {
                        put("path", fileDiag.file.path)
                        put("name", fileDiag.file.name)
                        put("items", buildJsonArray {
                            fileDiag.items.forEach { item ->
                                add(buildJsonObject {
                                    put("line", item.lineNumber)
                                    put("severity", if (item.severity == DiagnosticSeverity.ERROR) "error" else "warning")
                                    put("message", item.message)
                                })
                            }
                        })
                    })
                }
            })
        }
    }

    private fun getErrorsOnly(): JsonObject {
        val collector = DiagnosticCollectorService.getInstance(project)
        val errorFiles = collector.getAllProblemDiagnostics().filter { it.hasErrors }

        return buildJsonObject {
            put("errorFileCount", errorFiles.size)
            put("files", buildJsonArray {
                errorFiles.forEach { fileDiag ->
                    add(buildJsonObject {
                        put("path", fileDiag.file.path)
                        put("errors", buildJsonArray {
                            fileDiag.items.filter { it.severity == DiagnosticSeverity.ERROR }.forEach { item ->
                                add(buildJsonObject {
                                    put("line", item.lineNumber)
                                    put("message", item.message)
                                })
                            }
                        })
                    })
                }
            })
        }
    }

    // ========== Prompt Implementations ==========

    private fun getFixAllErrorsPrompt(): Pair<String, JsonArray> {
        val collector = DiagnosticCollectorService.getInstance(project)
        val errorFiles = collector.getAllProblemDiagnostics().filter { it.hasErrors }

        val content = if (errorFiles.isEmpty()) {
            "项目中没有发现错误。"
        } else {
            buildString {
                appendLine("我需要修复项目中的 ${errorFiles.size} 个错误文件：")
                appendLine()
                errorFiles.forEach { fileDiag ->
                    appendLine("文件: ${fileDiag.file.path}")
                    fileDiag.items.filter { it.severity == DiagnosticSeverity.ERROR }.forEach { item ->
                        appendLine("  - 行 ${item.lineNumber}: ${item.message}")
                    }
                    appendLine()
                }
                appendLine("请帮我：")
                appendLine("1. 分析每个错误的原因")
                appendLine("2. 提供修复后的代码")
                appendLine("3. 解释每个修改的原因")
            }
        }

        val messages = buildJsonArray {
            add(buildJsonObject {
                put("role", "user")
                put("content", buildJsonObject {
                    put("type", "text")
                    put("text", content)
                })
            })
        }

        return Pair("修复所有错误的提示词", messages)
    }

    private fun getCodeReviewPrompt(): Pair<String, JsonArray> {
        val collector = DiagnosticCollectorService.getInstance(project)
        val diagnostics = collector.getAllProblemDiagnostics()

        val content = buildString {
            appendLine("请基于以下诊断信息对代码进行审查：")
            appendLine()
            appendLine("项目: ${project.name}")
            appendLine("问题文件数: ${diagnostics.size}")
            appendLine()
            diagnostics.take(10).forEach { fileDiag ->
                appendLine("文件: ${fileDiag.file.name}")
                fileDiag.items.take(3).forEach { item ->
                    val type = if (item.severity == DiagnosticSeverity.ERROR) "错误" else "警告"
                    appendLine("  - [$type] 行${item.lineNumber}: ${item.message}")
                }
                if (fileDiag.items.size > 3) {
                    appendLine("  ... 还有 ${fileDiag.items.size - 3} 个问题")
                }
                appendLine()
            }
            appendLine("请提供：")
            appendLine("1. 代码质量评估")
            appendLine("2. 主要问题分析")
            appendLine("3. 改进建议")
        }

        val messages = buildJsonArray {
            add(buildJsonObject {
                put("role", "user")
                put("content", buildJsonObject {
                    put("type", "text")
                    put("text", content)
                })
            })
        }

        return Pair("代码审查提示词", messages)
    }

    // ========== Utilities ==========

    private fun parseQuery(query: String): Map<String, String> {
        return query.split("&")
            .mapNotNull { param ->
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }
            .toMap()
    }

    fun stopServer() {
        server?.stop(0)
    }
}

/**
 * 项目启动时初始化 MCP 服务器
 */
class DiagnosticMcpInitializer : ProjectActivity {
    override suspend fun execute(project: Project) {
        DiagnosticMcpServer.getInstance(project)
    }
}

package site.addzero.diagnostic.http

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.Alarm
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import site.addzero.diagnostic.model.DiagnosticSeverity
import site.addzero.diagnostic.service.DiagnosticCollectorService
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Problem4AI 本地 HTTP 服务。
 *
 * 只保留给本地 skill 调用的简单 JSON 接口，不再暴露 MCP 协议端点。
 */
@Service(Service.Level.PROJECT)
class DiagnosticHttpServer(private val project: Project) {

    private var server: HttpServer? = null
    private var port: Int = 0

    companion object {
        private val LOG: Logger = Logger.getInstance(DiagnosticHttpServer::class.java)
        private const val DEFAULT_PORT = 8964
        private var nextPort = DEFAULT_PORT

        fun getInstance(project: Project): DiagnosticHttpServer =
            project.getService(DiagnosticHttpServer::class.java)
    }

    init {
        startServer()
    }

    private fun startServer() {
        try {
            port = findAvailablePort()
            server = HttpServer.create(InetSocketAddress(port), 0).apply {
                createContext("/health", HealthHandler())
                createContext("/stats", StatsHandler())
                createContext("/diagnostics", DiagnosticsHandler())
                createContext("/api/v1/info", InfoHandler())
                createContext("/api/v1/stats", StatsHandler())
                createContext("/api/v1/diagnostics", DiagnosticsHandler())
                createContext("/api/v1/errors", ErrorsHandler())
                createContext("/api/v1/file-diagnostics", FileDiagnosticsHandler())
                createContext("/api/v1/refresh", RefreshHandler())
                createContext("/api/v1/fix-prompt", FixPromptHandler())

                executor = java.util.concurrent.Executors.newFixedThreadPool(2)
                start()
            }
            LOG.info("[Problem4AI][Http] server started at http://127.0.0.1:$port")
        } catch (e: Exception) {
            LOG.warn("[Problem4AI][Http] failed to start server", e)
        }
    }

    private fun findAvailablePort(): Int {
        var candidate = nextPort
        while (true) {
            try {
                java.net.ServerSocket(candidate).close()
                nextPort = candidate + 1
                return candidate
            } catch (_: Exception) {
                candidate++
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

    inner class HealthHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            sendJson(exchange, getHealthInfo().toString())
        }
    }

    inner class InfoHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            sendJson(exchange, getServerInfo().toString())
        }
    }

    inner class StatsHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            sendJson(exchange, buildStatsJson().toString())
        }
    }

    inner class DiagnosticsHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            sendJson(exchange, getDiagnosticsOverview().toString())
        }
    }

    inner class ErrorsHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            sendJson(exchange, getErrorsOnly().toString())
        }
    }

    inner class FileDiagnosticsHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val query = parseQuery(exchange.requestURI.rawQuery ?: "")
            val filePath = query["filePath"] ?: query["path"]
            val response = if (filePath.isNullOrBlank()) {
                buildJsonObject {
                    put("error", "filePath query parameter is required")
                }
            } else {
                handleGetFileDiagnostics(filePath)
            }
            sendJson(exchange, response.toString())
        }
    }

    inner class RefreshHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            sendJson(exchange, handleRefreshDiagnostics().toString())
        }
    }

    inner class FixPromptHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val query = parseQuery(exchange.requestURI.rawQuery ?: "")
            val filePath = query["filePath"] ?: query["path"]
            val response = if (filePath.isNullOrBlank()) {
                buildJsonObject {
                    put("error", "filePath query parameter is required")
                }
            } else {
                handleGenerateFixPrompt(filePath)
            }
            sendJson(exchange, response.toString())
        }
    }

    private fun getHealthInfo(): JsonObject {
        return buildJsonObject {
            put("status", "ok")
            put("port", port)
            put("project", project.name)
            put("projectBasePath", project.basePath ?: "")
        }
    }

    private fun getServerInfo(): JsonObject {
        return buildJsonObject {
            put("status", "ok")
            put("port", port)
            put("project", project.name)
            put("projectBasePath", project.basePath ?: "")
            put("httpBaseUrl", "http://127.0.0.1:$port")
            put("endpoints", buildJsonObject {
                put("info", "/api/v1/info")
                put("stats", "/api/v1/stats")
                put("diagnostics", "/api/v1/diagnostics")
                put("errors", "/api/v1/errors")
                put("fileDiagnostics", "/api/v1/file-diagnostics?filePath=")
                put("refresh", "/api/v1/refresh")
                put("fixPrompt", "/api/v1/fix-prompt?filePath=")
            })
        }
    }

    private fun buildStatsJson(): JsonObject {
        val collector = DiagnosticCollectorService.getInstance(project)
        val diagnostics = collector.getAllProblemDiagnostics()
        val errorCount = diagnostics.sumOf { diagnostic ->
            diagnostic.items.count { item -> item.severity == DiagnosticSeverity.ERROR }
        }
        val warningCount = diagnostics.sumOf { diagnostic ->
            diagnostic.items.count { item -> item.severity == DiagnosticSeverity.WARNING }
        }

        return buildJsonObject {
            put("project", project.name)
            put("projectBasePath", project.basePath ?: "")
            put("totalFiles", diagnostics.size)
            put("totalErrors", errorCount)
            put("totalWarnings", warningCount)
        }
    }

    private fun handleGetFileDiagnostics(filePath: String): JsonObject {
        val collector = DiagnosticCollectorService.getInstance(project)
        val fileDiagnostic = collector.getAllProblemDiagnostics().find { diagnostic ->
            diagnostic.file.path.contains(filePath) || diagnostic.file.name == filePath
        }

        return if (fileDiagnostic == null) {
            buildJsonObject {
                put("error", "File not found: $filePath")
            }
        } else {
            buildJsonObject {
                put("file", fileDiagnostic.file.path)
                put("items", buildJsonArray {
                    fileDiagnostic.items.forEach { item ->
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
        collector.performFullScan()

        return buildJsonObject {
            put("status", "scan triggered")
            put("project", project.name)
            put("projectBasePath", project.basePath ?: "")
            put("currentFileCount", collector.getAllProblemDiagnostics().size)
        }
    }

    private fun handleGenerateFixPrompt(filePath: String): JsonObject {
        val collector = DiagnosticCollectorService.getInstance(project)
        val fileDiagnostic = collector.getAllProblemDiagnostics().find { diagnostic ->
            diagnostic.file.path.contains(filePath) || diagnostic.file.name == filePath
        }

        val prompt = if (fileDiagnostic == null) {
            "File not found: $filePath"
        } else {
            buildString {
                appendLine("problem in \"${fileDiagnostic.file.path}\"")
                appendLine()
                fileDiagnostic.items.forEachIndexed { index, item ->
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
            put("filePath", filePath)
            put("prompt", prompt)
        }
    }

    private fun getDiagnosticsOverview(): JsonObject {
        val collector = DiagnosticCollectorService.getInstance(project)
        val diagnostics = collector.getAllProblemDiagnostics()

        return buildJsonObject {
            put("project", project.name)
            put("projectBasePath", project.basePath ?: "")
            put("fileCount", diagnostics.size)
            put("files", buildJsonArray {
                diagnostics.forEach { fileDiagnostic ->
                    add(buildJsonObject {
                        put("path", fileDiagnostic.file.path)
                        put("name", fileDiagnostic.file.name)
                        put("items", buildJsonArray {
                            fileDiagnostic.items.forEach { item ->
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
            put("project", project.name)
            put("projectBasePath", project.basePath ?: "")
            put("errorFileCount", errorFiles.size)
            put("files", buildJsonArray {
                errorFiles.forEach { fileDiagnostic ->
                    add(buildJsonObject {
                        put("path", fileDiagnostic.file.path)
                        put("errors", buildJsonArray {
                            fileDiagnostic.items.filter { it.severity == DiagnosticSeverity.ERROR }.forEach { item ->
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

    private fun parseQuery(query: String): Map<String, String> {
        return query.split("&")
            .mapNotNull { parameter ->
                val parts = parameter.split("=", limit = 2)
                if (parts.size != 2) {
                    null
                } else {
                    val key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8.name())
                    val value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name())
                    key to value
                }
            }
            .toMap()
    }

    fun stopServer() {
        server?.stop(0)
    }

    fun getPort(): Int = port
}

class DiagnosticHttpInitializer : ProjectActivity {
    companion object {
        private val LOG: Logger = Logger.getInstance(DiagnosticHttpInitializer::class.java)
        private const val STARTUP_HTTP_DELAY_MS = 5000
    }

    override suspend fun execute(project: Project) {
        val startupAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, project)
        startupAlarm.addRequest({
            if (project.isDisposed) {
                return@addRequest
            }
            runCatching {
                val server = DiagnosticHttpServer.getInstance(project)
                Problem4AiSkillInstaller.installBuiltinSkills(project, server)
                LOG.info("[Problem4AI][Http] delayed startup completed for project=${project.name}")
            }.onFailure { error ->
                LOG.warn("[Problem4AI][Http] delayed startup failed for project=${project.name}", error)
            }
        }, STARTUP_HTTP_DELAY_MS)
    }
}

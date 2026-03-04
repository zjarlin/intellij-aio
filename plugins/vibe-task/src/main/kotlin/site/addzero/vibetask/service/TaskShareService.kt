package site.addzero.vibetask.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import site.addzero.vibetask.model.ShareResult
import site.addzero.vibetask.model.ShareTarget
import site.addzero.vibetask.model.VibeTask
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64

@Service
class TaskShareService {

    private val logger = Logger.getInstance(TaskShareService::class.java)

    /**
     * 分享任务
     */
    fun shareTasks(
        tasks: List<VibeTask>,
        target: ShareTarget,
        githubToken: String? = null,
        giteeToken: String? = null
    ): ShareResult {
        return when (target) {
            ShareTarget.CLIPBOARD -> ShareResult(
                success = true,
                message = "已复制到剪贴板",
                target = target
            )
            ShareTarget.TEMP_LINK -> uploadToTempService(tasks)
            ShareTarget.GITHUB_GIST -> uploadToGitHubGist(tasks, githubToken)
            ShareTarget.GITEE_GIST -> uploadToGiteeGist(tasks, giteeToken)
        }
    }

    /**
     * 上传到临时文件服务 (0x0.st)
     * 优点：无需注册，匿名上传，自动过期
     */
    private fun uploadToTempService(tasks: List<VibeTask>): ShareResult {
        return try {
            val content = buildTaskContent(tasks)
            val url = URL("https://0x0.st")
            val connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "multipart/form-data; boundary=----WebKitFormBoundary")
            }

            val boundary = "----WebKitFormBoundary"
            val payload = buildString {
                append("--$boundary\r\n")
                append("Content-Disposition: form-data; name=\"file\"; filename=\"vibe-tasks.json\"\r\n")
                append("Content-Type: application/json\r\n\r\n")
                append(content)
                append("\r\n--$boundary--\r\n")
            }

            connection.outputStream.use { os ->
                os.write(payload.toByteArray(StandardCharsets.UTF_8))
            }

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }.trim()
                ShareResult(
                    success = true,
                    url = response,
                    message = "分享成功！链接有效期约30天",
                    target = ShareTarget.TEMP_LINK
                )
            } else {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                ShareResult(
                    success = false,
                    message = "上传失败: $error",
                    target = ShareTarget.TEMP_LINK
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to upload to temp service", e)
            ShareResult(
                success = false,
                message = "上传失败: ${e.message}",
                target = ShareTarget.TEMP_LINK
            )
        }
    }

    /**
     * 上传到 GitHub Gist
     */
    private fun uploadToGitHubGist(tasks: List<VibeTask>, token: String?): ShareResult {
        if (token.isNullOrBlank()) {
            return ShareResult(
                success = false,
                message = "请先配置 GitHub Token",
                target = ShareTarget.GITHUB_GIST
            )
        }

        return try {
            val url = URL("https://api.github.com/gists")
            val connection = url.openConnection() as HttpURLConnection

            val jsonBody = buildGistJson(tasks, "Vibe Tasks Export")

            connection.apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Authorization", "token $token")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
            }

            connection.outputStream.use { os ->
                os.write(jsonBody.toByteArray(StandardCharsets.UTF_8))
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val htmlUrl = extractJsonValue(response, "html_url")
                ShareResult(
                    success = true,
                    url = htmlUrl,
                    message = "已创建 GitHub Gist",
                    target = ShareTarget.GITHUB_GIST
                )
            } else {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                ShareResult(
                    success = false,
                    message = "创建失败: $error",
                    target = ShareTarget.GITHUB_GIST
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to create GitHub Gist", e)
            ShareResult(
                success = false,
                message = "创建失败: ${e.message}",
                target = ShareTarget.GITHUB_GIST
            )
        }
    }

    /**
     * 上传到 Gitee Gist
     */
    private fun uploadToGiteeGist(tasks: List<VibeTask>, token: String?): ShareResult {
        if (token.isNullOrBlank()) {
            return ShareResult(
                success = false,
                message = "请先配置 Gitee Token",
                target = ShareTarget.GITEE_GIST
            )
        }

        return try {
            val url = URL("https://gitee.com/api/v5/gists")
            val connection = url.openConnection() as HttpURLConnection

            val jsonBody = buildGistJson(tasks, "Vibe Tasks Export")

            connection.apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }

            // Gitee 使用 access_token 作为查询参数
            val outputStream = connection.outputStream
            OutputStreamWriter(outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(jsonBody)
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val htmlUrl = extractJsonValue(response, "html_url")
                ShareResult(
                    success = true,
                    url = htmlUrl,
                    message = "已创建 Gitee Gist",
                    target = ShareTarget.GITEE_GIST
                )
            } else {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                ShareResult(
                    success = false,
                    message = "创建失败: $error",
                    target = ShareTarget.GITEE_GIST
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to create Gitee Gist", e)
            ShareResult(
                success = false,
                message = "创建失败: ${e.message}",
                target = ShareTarget.GITEE_GIST
            )
        }
    }

    /**
     * 构建 Gist JSON
     */
    private fun buildGistJson(tasks: List<VibeTask>, description: String): String {
        val content = buildTaskContent(tasks)
        // 转义 JSON 字符串
        val escapedContent = content
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

        return """
            {
                "description": "$description",
                "public": false,
                "files": {
                    "vibe-tasks.json": {
                        "content": "$escapedContent"
                    }
                }
            }
        """.trimIndent()
    }

    /**
     * 构建任务内容
     */
    private fun buildTaskContent(tasks: List<VibeTask>): String {
        return buildString {
            appendLine("{")
            appendLine("  \"version\": 3,")
            appendLine("  \"exportTime\": ${System.currentTimeMillis()},")
            appendLine("  \"taskCount\": ${tasks.size},")
            appendLine("  \"tasks\": [")

            tasks.forEachIndexed { index, task ->
                append("    {")
                append("\"id\":\"${escapeJson(task.id)}\",")
                append("\"content\":\"${escapeJson(task.content)}\",")
                append("\"projectPath\":\"${escapeJson(task.projectPath)}\",")
                append("\"projectName\":\"${escapeJson(task.projectName)}\",")
                append("\"moduleName\":\"${escapeJson(task.moduleName)}\",")
                append("\"modulePath\":\"${escapeJson(task.modulePath)}\",")
                append("\"status\":\"${task.status.name}\",")
                append("\"priority\":\"${task.priority.name}\",")
                append("\"assignees\":[${task.assignees.joinToString(",") { "\"${escapeJson(it)}\"" }}],")
                append("\"createdAt\":${task.createdAt},")
                append("\"completedAt\":${task.completedAt ?: "null"},")
                append("\"tags\":[${task.tags.joinToString(",") { "\"${escapeJson(it)}\"" }}]")
                append("}")
                if (index < tasks.size - 1) appendLine(",") else appendLine()
            }

            appendLine("  ]")
            appendLine("}")
        }
    }

    /**
     * 从 JSON 响应中提取字段值
     */
    private fun extractJsonValue(json: String, key: String): String? {
        val regex = """"$key"\s*:\s*"([^"]+)"""".toRegex()
        return regex.find(json)?.groupValues?.get(1)
    }

    private fun escapeJson(str: String): String {
        return str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
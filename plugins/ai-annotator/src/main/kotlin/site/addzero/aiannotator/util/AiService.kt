package site.addzero.aiannotator.util

import site.addzero.aiannotator.settings.AiAnnotatorSettingsService
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

object AiService {

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    private val gson = Gson()

    /**
     * 批量获取字段注释
     * @param fields Map<字段名, 字段对象>
     * @return Map<字段名, AI生成的注释>
     */
    fun batchGetComments(fields: Map<String, Any>): Map<String, String>? {
        val settings = AiAnnotatorSettingsService.getInstance().state
        
        if (!settings.enableAiGuessing || settings.aiApiKey.isBlank()) {
            return null
        }

        if (!settings.enableBatchProcessing) {
            return fields.keys.associateWith { fieldName ->
                getSingleComment(fieldName) ?: ""
            }
        }

        val prompt = buildBatchPrompt(fields.keys.toList())
        val response = callAiApi(prompt) ?: return null

        return parseAiResponse(response, fields.keys.toList())
    }

    /**
     * 获取单个字段的注释
     */
    fun getSingleComment(fieldName: String): String? {
        val settings = AiAnnotatorSettingsService.getInstance().state
        
        if (!settings.enableAiGuessing || settings.aiApiKey.isBlank()) {
            return null
        }

        val prompt = "请为Java/Kotlin类的字段生成简洁的中文注释。字段名: $fieldName\n" +
                     "只需返回注释文本，不要包含任何其他内容。"

        return callAiApi(prompt)
    }

    private fun buildBatchPrompt(fieldNames: List<String>): String {
        return """
            请为以下Java/Kotlin类的字段生成简洁的中文注释。
            
            字段列表:
            ${fieldNames.joinToString("\n") { "- $it" }}
            
            要求:
            1. 返回JSON格式，key为字段名，value为注释文本
            2. 注释要简洁明了，一般5-15字
            3. 根据字段名的语义推测其用途
            
            示例格式:
            {
              "userId": "用户ID",
              "userName": "用户名称",
              "createTime": "创建时间"
            }
        """.trimIndent()
    }

    private fun callAiApi(prompt: String): String? {
        val settings = AiAnnotatorSettingsService.getInstance().state
        
        try {
            val requestBody = buildRequestBody(prompt, settings)
            val request = HttpRequest.newBuilder()
                .uri(URI.create("${settings.aiBaseUrl}/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer ${settings.aiApiKey}")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() != 200) {
                println("AI API 调用失败: ${response.statusCode()} - ${response.body()}")
                return null
            }

            return extractContentFromResponse(response.body())
        } catch (e: Exception) {
            println("AI API 调用异常: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    private fun buildRequestBody(prompt: String, settings: site.addzero.aiannotator.settings.AiAnnotatorSettings): String {
        val requestJson = JsonObject().apply {
            addProperty("model", settings.aiModel)
            addProperty("temperature", settings.temperature)
            add("messages", gson.toJsonTree(listOf(
                mapOf("role" to "user", "content" to prompt)
            )))
        }
        return gson.toJson(requestJson)
    }

    private fun extractContentFromResponse(responseBody: String): String? {
        return try {
            val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
            jsonResponse.getAsJsonArray("choices")
                ?.get(0)?.asJsonObject
                ?.getAsJsonObject("message")
                ?.get("content")?.asString
        } catch (e: Exception) {
            println("解析 AI 响应失败: ${e.message}")
            null
        }
    }

    private fun parseAiResponse(response: String, fieldNames: List<String>): Map<String, String> {
        return try {
            // 尝试提取 JSON 部分
            val jsonStart = response.indexOf("{")
            val jsonEnd = response.lastIndexOf("}") + 1
            
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val jsonStr = response.substring(jsonStart, jsonEnd)
                val jsonObject = gson.fromJson(jsonStr, JsonObject::class.java)
                
                fieldNames.associateWith { fieldName ->
                    jsonObject.get(fieldName)?.asString ?: ""
                }
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            println("解析 AI 批量响应失败: ${e.message}")
            emptyMap()
        }
    }
}

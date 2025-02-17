package com.addzero.addl.ai.util.ai.ollama

import cn.hutool.core.util.StrUtil
import cn.hutool.http.ContentType
import cn.hutool.http.HttpRequest
import com.addzero.addl.ai.util.ai.AiUtil
import com.addzero.addl.ai.util.ai.Promt.AICODER
import com.addzero.addl.ai.util.ai.ctx.AiCtx
import com.addzero.addl.ktututil.parseObject
import com.addzero.addl.ktututil.toJson
import com.addzero.addl.settings.SettingContext
import com.addzero.addl.util.JlStrUtil.extractMarkdownBlockContent
import com.addzero.common.kt_util.addPrefixIfNot
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper



// 声明两个子类
class DeepSeekAiUtil(modelName: String, question: String, promptTemplate: String = "") : AiUtil(modelName, question, promptTemplate) {

    val settings = SettingContext.settings
//    private val url: String? = settings.ollamaUrl
//    private val model: String? = settings.modelNameOffline



    data class DeepSeekRequest(
        val messages: List<Message>,
        val model: String,
        val stream: Boolean,
    )

    data class Message(
        val content: String,
        val role: String,
    )

    fun askDeepSeek(message: String, prompt: String? = ""): String? {
        val deepSeekRequest = DeepSeekRequest(
            model = "deepseek-coder", stream = false, messages = listOf(
                Message(
                    role = "system", content = AICODER.trimIndent()
                ), Message(
                    role = "user", content = prompt + message
                )
            )
        )
        // 发送 POST 请求
        val modelNameOnline = settings.modelNameOnline
        val apikey = System.getenv("DEEPSEEK_API_KEY") ?: settings.modelKey
        val addPrefixIfNot = apikey.addPrefixIfNot("Bearer ")
        val response = HttpRequest.post("https://api.deepseek.com/chat/completions").body(deepSeekRequest.toJson(), ContentType.JSON.toString()) // 设置请求体和
            // Content-Type
            .header("Authorization", addPrefixIfNot) // 设置 Authorization
            .header("Cookie", "HWWAFSESID=8de64226e01ce83b61d; HWWAFSESTIME=1736583106731") // 设置 Cookie
            .execute() // 执行请求
        val body = response.body()

        // 创建 ObjectMapper 实例
        val mapper = jacksonObjectMapper()

        // 解析 JSON
        val rootNode = mapper.readTree(body)
        val content =

        try {// 提取 content 字段
            rootNode["choices"][0]["message"]["content"].asText()
        } catch (e: Exception) {
            return null
        }

        return content
    }


    override fun ask(clazz: Class<*>): String {
        val (newPrompt, quesCtx) = AiCtx.structuredOutputContext(question, promptTemplate, clazz)
//        val format = StrUtil.format(newPrompt, quesCtx)
//        val askqwen = askollama(question, format)

        val ask = ask(newPrompt, quesCtx)
        return ask

    }

    override fun ask(json: String, comment: String): String {
        val (newPrompt, quesCtx) = AiCtx.structuredOutputContext(question, promptTemplate, json, comment)
        val ask = ask(newPrompt, quesCtx)
        return ask
    }

    private fun ask(newPrompt: String, quesCtx: Map<String, String?>): String {
        val format = StrUtil.format(newPrompt, quesCtx)
        val askqwen = askDeepSeek(question, format)

        val s = try {
            val parseObject = askqwen?.parseObject(OllamaResponse::class.java)
            val response = parseObject?.response
            val extractMarkdownBlockContent = response?.let { extractMarkdownBlockContent(it) }
            extractMarkdownBlockContent
        } catch (e: Exception) {
            askqwen
        }

        return s ?: ""
    }

}
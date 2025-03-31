package com.addzero.addl.ai.util.ai.ollama

import cn.hutool.core.util.StrUtil
import cn.hutool.http.ContentType
import cn.hutool.http.HttpRequest
import com.addzero.addl.ai.consts.ChatModels.DeepSeekOnlineModel
import com.addzero.addl.ai.util.ai.AiUtil
import com.addzero.addl.ai.util.ai.Promt.AICODER
import com.addzero.addl.ai.util.ai.ctx.AiCtx
import com.addzero.addl.ktututil.toJson
import com.addzero.addl.settings.SettingContext
import com.addzero.addl.util.JlStrUtil.extractMarkdownBlockContent
import com.addzero.common.kt_util.addPrefixIfNot
import com.alibaba.fastjson.JSON


// 声明两个子类
class DeepSeekAiUtil(modelName: String, question: String, promptTemplate: String = "") : AiUtil(modelName, question, promptTemplate) {

    val settings = SettingContext.settings

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
        var modelNameOnline = settings.modelNameOnline
        if (modelNameOnline.contains("qwen")) {
            modelNameOnline=DeepSeekOnlineModel
        }


        val deepSeekRequest = DeepSeekRequest(
            model = modelNameOnline, stream = false, messages = listOf(
                Message(
                    role = "system", content = AICODER.trimIndent()
                ), Message(
                    role = "user", content = prompt + message
                )
            )
        )
        // 发送 POST 请求
        val apikey = settings.modelKey .ifBlank { System.getenv("DEEPSEEK_API_KEY") ?: "" }

        if (apikey.isBlank()) {
//            DialogUtil.showErrorMsg("DEEPSEEK_API_KEY未配置")
            throw RuntimeException("DEEPSEEK_API_KEY未配置")
            return null
        }

        val addPrefixIfNot = apikey.addPrefixIfNot("Bearer ")
        val response = HttpRequest.post("https://api.deepseek.com/chat/completions").body(deepSeekRequest.toJson(), ContentType.JSON.toString()) // 设置请求体和
            // Content-Type
            .header("Authorization", addPrefixIfNot) // 设置 Authorization
//            .header("Cookie", "HWWAFSESID=8de64226e01ce83b61d; HWWAFSESTIME=1736583106731") // 设置 Cookie
            .execute() // 执行请求
        val body = response.body()
        if (body.contains("{\"error\":{\"message")) {
            throw RuntimeException(body)
        }

        try {
            // 使用FastJson解析响应
            val jsonObject = JSON.parseObject(body)
            val string = jsonObject.getJSONArray("choices")
                ?.getJSONObject(0)
                ?.getJSONObject("message")
                ?.getString("content")
            return string
        } catch (e: Exception) {
            return null
        }
    }


    override fun ask(clazz: Class<*>): String {
        val (newPrompt, quesCtx) = AiCtx.structuredOutputContext(question, promptTemplate, clazz)
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
//            val parseObject = askqwen?.parseObject(OllamaResponse::class.java)
//            val response = parseObject?.response
            val extractMarkdownBlockContent = askqwen?.let { extractMarkdownBlockContent(it) }
            extractMarkdownBlockContent
        } catch (e: Exception) {
            askqwen
        }

        return s ?: ""
    }

}


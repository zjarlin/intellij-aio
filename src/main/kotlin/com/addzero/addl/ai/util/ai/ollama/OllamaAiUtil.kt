package com.addzero.addl.ai.util.ai.ollama

import cn.hutool.core.util.StrUtil
import cn.hutool.http.HttpUtil
import com.addzero.addl.ai.util.ai.AiUtil
import com.addzero.addl.ai.util.ai.ctx.AiCtx
import com.addzero.addl.ktututil.parseObject
import com.addzero.addl.ktututil.toJson
import com.addzero.addl.settings.SettingContext
import com.addzero.addl.util.JlStrUtil.extractMarkdownBlockContent


class Res {

}

// 声明两个子类
class OllamaAiUtil(modelName: String, question: String, promptTemplate: String = "") :
    AiUtil(modelName, question, promptTemplate) {

    val settings = SettingContext.settings
    private val url: String? = settings.ollamaUrl

    private val model: String? = settings.modelNameOffline

    val chatApi: String = "/api/generate"

    class Message(
        private val content: String? = null,
        val role: String? = null,
    )

    class ChatRequestParam(
        private val model: String? = null,
        private val stream: Boolean? = null,
//        private val format: String? = "json",
//        private val messages: kotlin.collections.List<Message>? = emptyList(),
        private val prompt: String? = "Respond using JSON",
    )

    fun askollama(message: String, prompt: String?): String? {
//    val buildMainContent = buildMainContent(message,prompt)
        val buildMainContent = mapOf(
            "model" to model, "prompt" to prompt, "stream" to false
//            , "messages" to emptyList<Message>()
        )
        val toJson = buildMainContent.toJson()
        val post = HttpUtil.post(url + chatApi, toJson)
        return post
    }

    private fun buildMainContent(msg: String, prompt: String?): String {
        val chatRequestParam = ChatRequestParam(model, false, prompt)
        val toJson = chatRequestParam.toJson()
        return toJson
    }

    override fun ask(clazz: Class<*>): String {
        val (newPrompt, quesCtx) = AiCtx.structuredOutputContext(question, promptTemplate, clazz)
//        val format = StrUtil.format(newPrompt, quesCtx)
//        val askqwen = askollama(question, format)


        val ask = ask(newPrompt, quesCtx)
        return ask


//        return askqwen ?: ""

    }

    override fun ask(json: String, comment: String): String {
        val (newPrompt, quesCtx) = AiCtx.structuredOutputContext(question, promptTemplate, json, comment)
        val ask = ask(newPrompt, quesCtx)
        return ask
    }

    private fun ask(newPrompt: String, quesCtx: Map<String, String?>): String {
        val format = StrUtil.format(newPrompt, quesCtx)
        val askqwen = askollama(question, format)

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
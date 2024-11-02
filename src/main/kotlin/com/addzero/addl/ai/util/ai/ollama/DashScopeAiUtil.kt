package com.addzero.addl.ai.util.ai.ollama

import MyMessage
import Qwendto
import cn.hutool.core.util.StrUtil
import cn.hutool.http.HttpRequest
import com.addzero.addl.ai.util.ai.AiUtil
import com.addzero.addl.ai.util.ai.ctx.AiCtx
import com.addzero.addl.ktututil.parseObject
import com.addzero.addl.ktututil.toJson
import com.addzero.addl.settings.MyPluginSettingsService
import com.addzero.addl.util.Dba
import com.addzero.addl.util.JlStrUtil.extractMarkdownBlockContent

class DashScopeAiUtil(modelName: String, question: String, promptTemplate: String = "") :
    AiUtil(modelName, question, promptTemplate) {

    fun askqwen(question: String, prompt: String): String? {
        val settings = MyPluginSettingsService.getInstance().state
// 修改设置项
        val getenvBySetting = settings.modelKey
        // 构建请求内容
        val model = settings.modelNameOnline

        val getenvBySys = System.getenv("DASHSCOPE_API_KEY")
        val apiKey = StrUtil.firstNonBlank(getenvBySetting, getenvBySys)
        if (apiKey.isBlank()) {
            throw RuntimeException("请设置环境变量 DASHSCOPE_API_KEY")
        }
        val baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
        val qwendto = Qwendto(model, listOf(MyMessage("system", prompt), MyMessage("user", question)))
        val toJson = qwendto.toJson()

        // 发送POST请求，包含Authorization和Content-Type头
        val response = HttpRequest.post(baseUrl).header("Authorization", "Bearer $apiKey")  // 设置Authorization头
            .header("Content-Type", "application/json")  // 设置Content-Type头
            .body(toJson)  // 设置请求体
            .execute()
        // 返回响应内容
        val body = response.body()

        val parseObject = body?.parseObject(Dba::class.java)
        val joinToString = parseObject?.choices?.map {
            val content = it?.message?.content
            content
        }?.joinToString()
        val let = joinToString?.let { extractMarkdownBlockContent(it) }
        return let
    }


    override fun ask(clazz: Class<*>): String {
        val (newPrompt, quesCtx) = AiCtx.structuredOutputContext(question, promptTemplate, clazz)
        val format = StrUtil.format(newPrompt, quesCtx)
        val askqwen = askqwen(question, format)
        return askqwen ?: ""
    }

    override fun ask(json: String, comment: String): String {
        val (newPrompt, quesCtx) = AiCtx.structuredOutputContext(question, promptTemplate, json, comment)
        return ask(newPrompt, quesCtx)
    }

    private fun ask(newPrompt: String, quesCtx: Map<String, String?>): String {
        val format = StrUtil.format(newPrompt, quesCtx)
        val askqwen = askqwen(question, format)
        return askqwen ?: ""
    }
}
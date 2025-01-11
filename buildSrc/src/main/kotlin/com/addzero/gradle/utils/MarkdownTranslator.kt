package com.addzero.gradle.utils

import cn.hutool.core.util.StrUtil
import cn.hutool.http.HttpRequest
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject

/**
 * Markdown 翻译工具类
 * @author zjarlin
 */
object MarkdownTranslator {
    private const val TRANSLATION_API = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"

    /**
     * 翻译中文 Markdown 为英文并追加到原文上方
     * @param content 原始内容
     * @param apiKey DashScope API Key
     * @return 处理后的内容
     */
    fun translateAndAppend(content: String, apiKey: String): String {
        // 如果已经包含英文部分，直接返回
        if (content.startsWith("### English:")) {
            return content
        }

        // 提取需要翻译的文本
        val textToTranslate = content.split("\n").joinToString("\n") { line ->
            when {
                line.startsWith("# ") -> line.substring(2)
                line.startsWith("## ") -> line.substring(3)
                line.startsWith("### ") -> line.substring(4)
                line.startsWith("- ") -> line.substring(2)
                else -> line
            }
        }

        // 调用翻译 API
        val translatedText = translateToEnglish(textToTranslate, apiKey)

        // 构建最终内容
        return buildString {
            appendLine("### English:")
            appendLine()
            appendLine(translatedText)
            appendLine()
            appendLine("### 中文：")
            appendLine()
            append(content)
        }
    }

    /**
     * 调用 DashScope API 进行翻译
     */
    private fun translateToEnglish(text: String, apiKey: String): String {
        if (StrUtil.isBlank(apiKey)) {
            throw IllegalArgumentException("DashScope API Key is required")
        }

        val prompt = """
            请将以下中文内容翻译成英文，要求：
            1. 保持专业和准确性
            2. 保留原始的换行格式
            3. 对于技术术语使用通用的英文表达
            4. 保持文档风格的正式性
            
            待翻译内容：
            
            $text
        """.trimIndent()
        
        val requestBody = JSONObject().apply {
            put("model", "qwen-max")
            put("messages", listOf(
                mapOf(
                    "role" to "system",
                    "content" to "你是一个专业的翻译助手，专注于将中文技术文档翻译成英文。请保持专业性和准确性，同时确保翻译后的文档易于理解。"
                ),
                mapOf(
                    "role" to "user",
                    "content" to prompt
                )
            ))
        }

        val response = HttpRequest.post(TRANSLATION_API)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .body(requestBody.toJSONString())
            .execute()

        val responseBody = response.body()
        if (response.status != 200 || responseBody.isNullOrBlank()) {
            throw RuntimeException("Translation failed: ${response.status}")
        }

        return JSON.parseObject(responseBody)
            ?.getJSONArray("choices")
            ?.getJSONObject(0)
            ?.getJSONObject("message")
            ?.getString("content")
            ?.trim()
            ?: throw RuntimeException("Failed to parse translation response")
    }
} 
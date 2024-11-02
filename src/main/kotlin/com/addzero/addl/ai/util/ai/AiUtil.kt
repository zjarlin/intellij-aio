package com.addzero.addl.ai.util.ai

import cn.hutool.core.util.ReflectUtil
import com.addzero.addl.ai.consts.ChatModels.DASH_SCOPE
import com.addzero.addl.ai.consts.ChatModels.OLLAMA
import com.addzero.addl.ai.util.ai.ollama.DashScopeAiUtil
import com.addzero.addl.ai.util.ai.ollama.OllamaAiUtil
import com.addzero.addl.settings.SettingContext
import com.addzero.addl.util.fieldinfo.getSimpleFieldInfoStr
import java.util.stream.Collectors


abstract class AiUtil(
    private val modelName: String = SettingContext.settings.modelNameOnline,
    protected val question: String,
    protected val promptTemplate: String = "",
) {



    /**
     * 构建结构化输出提示
     * @param [fieldComment] 字段注释
     * @return [String]
     */
    private fun buildStructureOutPutPrompt(fieldComment: Map<String, String>): String {
        return fieldComment.entries.joinToString { "${it.key}:${it.value}" }
    }

    /**
     * 构建结构化输出提示
     * @param [formatClass] 类
     * @return [String]
     */
    private fun <T> buildStructureOutPutPrompt(formatClass: Class<T>?): String {
        return if (formatClass != null) "Expected output: ${formatClass.simpleName}" else ""
    }


    /**
     * 生成答案的抽象方法，需要由子类实现
     * @param [input] 输入数据
     *
     * @return [String] 答案
     */
    abstract fun ask(clazz: Class<*>): String

    /**
     * 生成答案的抽象方法，需要由子类实现
     * @param [input] 输入数据
     * @return [String] 答案
     */
    abstract fun ask(json: String, comment: String): String


    companion object {

        // 根据modelName动态返回子类实例的静态方法
        fun INIT(modelName: String, question: String, promptTemplate: String = ""): AiUtil {
            return when (modelName) {
                OLLAMA -> OllamaAiUtil(modelName, question, promptTemplate)
                DASH_SCOPE -> DashScopeAiUtil(modelName, question, promptTemplate)
                else -> throw IllegalArgumentException("Unknown modelName: $modelName")
            }
        }


        fun INIT(question: String, promptTemplate: String = ""): AiUtil {
            val (modelKey, modelManufacturer, modelNameOnline, ollamaUrl, modelNameOffline, temPerature, dbType) = SettingContext.settings
            return when (modelManufacturer) {
                OLLAMA -> OllamaAiUtil(modelNameOffline, question, promptTemplate)
                DASH_SCOPE -> DashScopeAiUtil(modelNameOnline, question, promptTemplate)
                else -> throw IllegalArgumentException("Unknown modelName")
            }
        }


        fun buildStructureOutPutPrompt(fieldComment: Map<String, String>): String {
            val collect2 = fieldComment.entries.stream().map { e: Map.Entry<String, String> ->
                val key = e.key
                val value = e.value
                val s = "$key:$value"
                s
            }.collect(Collectors.joining(System.lineSeparator()))
            return collect2
        }


        fun buildStructureOutPutPrompt(clazz: Class<*>?): String {
            if (clazz == null) {
                return ""
            }
            val fieldInfosRecursive = getSimpleFieldInfoStr(clazz)
// 收集所有字段及其描述
            val fieldDescriptions = StringBuilder()
            val fields = ReflectUtil.getFields(clazz)
// 过滤带有 @field:JsonPropertyDescription 注解的字段
            val ret = emptyList<String>()
// 返回生成的描述信息
            val prompt = """
结构化输出字段定义 内容如下:
$fieldInfosRecursive
""".trimIndent()
            return prompt
        }


//        fun toEventStream(spec: ChatClientRequestSpec): Flux<ServerSentEvent<String>> {
//            val httpServletResponse: HttpServletResponse? = SpringUtil.getBean(HttpServletResponse::class.java)
//            if (httpServletResponse != null) {
//                httpServletResponse.contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE
//            }
//
//            return spec.stream().chatResponse().map { chatResponse: ChatResponse ->
//                ServerSentEvent.builder(chatResponse.toJson()).event("message").build()
//            }
//        }

    }

}
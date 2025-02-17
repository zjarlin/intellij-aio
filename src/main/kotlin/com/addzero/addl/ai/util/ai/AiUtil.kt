package com.addzero.addl.ai.util.ai

import cn.hutool.core.util.ReflectUtil
import com.addzero.addl.ai.agent.dbdesign.FieldDTOUseDocList
import com.addzero.addl.ai.consts.ChatModels.DASH_SCOPE
import com.addzero.addl.ai.consts.ChatModels.DeepSeek
import com.addzero.addl.ai.consts.ChatModels.OLLAMA
import com.addzero.addl.ai.util.ai.ollama.DashScopeAiUtil
import com.addzero.addl.ai.util.ai.ollama.DeepSeekAiUtil
import com.addzero.addl.ai.util.ai.ollama.OllamaAiUtil
import com.addzero.addl.ktututil.parseObject
import com.addzero.addl.ktututil.toJson
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
                DeepSeek-> DeepSeekAiUtil(modelName, question, promptTemplate)
                else -> throw IllegalArgumentException("Unknown modelName: $modelName")
            }
        }


        fun INIT(question: String, promptTemplate: String = ""): AiUtil {
            val settings = SettingContext.settings
            val modelManufacturer = settings.modelManufacturer
            val modelNameOffline = settings.modelNameOffline
            val modelNameOnline = settings.modelNameOnline
            return when (modelManufacturer) {
                OLLAMA -> OllamaAiUtil(modelNameOffline, question, promptTemplate)
                DASH_SCOPE -> DashScopeAiUtil(modelNameOnline, question, promptTemplate)
                DeepSeek-> DeepSeekAiUtil(modelNameOnline, question, promptTemplate)
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

        fun batchGetComments(noCommentFields: MutableMap<String, Any>): Map<out String?, String>? {
            val keys = noCommentFields.keys
            val associate: Map<String, String> = keys.associateWith { it }

            val ask = AiUtil.INIT(
                keys.toJson(), """
中括号中的字段名尽可能的推测生成对应的注释信息，字段可能是拼音命名风格,也可能是英文风格,实在推测不出字段什么意思的可以返回空字符串: 
            """.trimIndent()
            ).ask(FieldDTOUseDocList::class.java)

            try {
                val translated = ask.parseObject (FieldDTOUseDocList::class.java)

                val fieldInfo = translated.fieldInfo
                if (fieldInfo?.isEmpty() == true) {
                    return null
                }

                val associate = fieldInfo?.associate { it.fieldName to it.fieldChineseName }
                return associate
            } catch (e: Exception) {
                return associate
            }


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
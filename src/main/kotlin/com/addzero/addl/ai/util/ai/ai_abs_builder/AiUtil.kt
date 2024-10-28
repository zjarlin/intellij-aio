package com.addzero.ai.modules.ai.util.ai.ai_abs_builder

import cn.hutool.core.util.ArrayUtil
import cn.hutool.core.util.ReflectUtil
import cn.hutool.core.util.StrUtil
import cn.hutool.extra.spring.SpringUtil
import com.addzero.addl.ai.config.DefaultCtx
import com.addzero.addl.ai.util.ai.ctx.AiCtx
import com.addzero.addl.ai.util.ai.ctx.AiCtx.structuredOutputContext
import com.addzero.addl.util.fieldinfo.getSimpleFieldInfoStr
import com.addzero.ai.modules.ai.util.ai.Promt.PROMT_HIS
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.chat.prompt.SystemPromptTemplate
import org.springframework.ai.converter.ListOutputConverter
import org.springframework.ai.model.Media
import org.springframework.core.ParameterizedTypeReference
import org.springframework.core.convert.support.DefaultConversionService
import org.springframework.util.MimeType
import org.springframework.web.multipart.MultipartFile
import java.util.*
import java.util.stream.Collectors


class AiUtil(
    private val modelName: String = DefaultCtx.defaultChatModelName,
    private val question: String,
    private val promptTemplate: String = "",
) {


    /**
     * 通用问答接口
     * @param [question] 问题
     * @param [promptTemplate] 提示词
     * @param [formatClass] 返回的对象类型
     * @return [T]
     */
    fun <T> ask(formatClass: Class<T>): T {
        val chatClient = AiCtx.defaultChatClient(modelName!!)
        return ask(chatClient, formatClass)
    }

    private fun <T> ask(chatClient: ChatClient, formatClass: Class<T>): T {
        val (newPrompt, quesCtx) = structuredOutputContext(question, promptTemplate, formatClass)
        return chatClient.prompt().user { u: ChatClient.PromptUserSpec ->
            u.text(newPrompt).params(quesCtx)
        }.call().entity(formatClass)
    }

    private fun ask(chatClient: ChatClient, formatJson: String, jsonComment: String): String {
        val (newPrompt, quesCtx) = structuredOutputContext(question, promptTemplate, formatJson, jsonComment)
        return chatClient.prompt().user { u: ChatClient.PromptUserSpec -> u.text(newPrompt).params(quesCtx) }.call()
            .content()!!
    }

    /**
     * 问答接口，返回Map形式
     * @param [fieldComment] 字段注释
     * @return [Map<String, Any>]
     */
    fun ask(fieldComment: Map<String, String>): Map<String, Any> {
        val promptWithFields = promptTemplate + "以下是结构化输出字段定义${buildStructureOutPutPrompt(fieldComment)}"
        val chatClient = AiCtx.defaultChatClient(modelName)
        val call = chatClient.prompt().user { u: ChatClient.PromptUserSpec ->
            u.text(promptWithFields).param("question", question)
        }.call()
        return call.entity(object : ParameterizedTypeReference<Map<String, Any>>() {})
    }

    /**
     * 问答接口，返回List形式
     * @param [question] 问题
     * @param [promptTemplate] 提示词
     * @param [fieldComment] 字段注释
     * @return [List<String>]
     */
    fun <C : ChatModel> askList(fieldComment: Map<String, String>): List<String> {
        val promptWithFields = promptTemplate + "以下是结构化输出字段定义${buildStructureOutPutPrompt(fieldComment)}"
        val chatClient = AiCtx.defaultChatClient(modelName)
        val call = chatClient.prompt().user { u: ChatClient.PromptUserSpec ->
            u.text(promptWithFields).param("question", question)
        }.call()
        return call.entity(ListOutputConverter(DefaultConversionService()))
    }

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

    fun ask(formatJson: String, jsonComment: String): String {
        val defaultChatClient = AiCtx.defaultChatClient(modelName)

        val aiUtil = AiUtil(modelName, question, promptTemplate)
        val ask1 = aiUtil.ask(chatClient = defaultChatClient, formatJson = formatJson, jsonComment = jsonComment)
        return ask1

    }

    companion object {
        fun <T> ask(ques: String, template: String, modelName: String, java: Class<T>): T {
            val ask = AiUtil(modelName, ques, template).ask(java)
            return ask
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


        /**
         * 让AI判断是否需要用到历史记录
         *
         * @param message
         * @return [Boolean]
         */
        fun caculateUseChatHistory(message: String): Boolean {
            val chatClient = SpringUtil.getBean(ChatClient::class.java)

            val SYSTEM_PROMPT = PROMT_HIS.trimIndent()


            val promptTemplate = SystemPromptTemplate(SYSTEM_PROMPT)
            val systemMessage = promptTemplate.createMessage(java.util.Map.of<String, Any>("message", message))

            val userMessage = UserMessage(message)
            val spec = chatClient.prompt(Prompt(java.util.List.of(systemMessage, userMessage)))

            val call = spec.call()

            val content = call.chatResponse().result.output.content
            val b = StrUtil.containsIgnoreCase(content, "true")
            return b
        }

        /**
         * 转为AI媒体
         *
         * @param files
         * @return [Media[]]
         */
        fun convertMultipartFilesToMedias(files: Array<MultipartFile>?): Array<Media?> {
            if (ArrayUtil.isEmpty(files)) {
                return arrayOfNulls(0)
            }
            val array = Arrays.stream<MultipartFile>(files).map<Media> { e: MultipartFile ->
                val contentType = e.contentType
                val resource = e.resource
                val media = Media(contentType?.let { MimeType.valueOf(it) }, resource)
                media
            }.toArray { arrayOfNulls<Media>(it) }
            return array
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
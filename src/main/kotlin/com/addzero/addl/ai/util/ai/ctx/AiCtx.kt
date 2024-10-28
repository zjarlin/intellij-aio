package com.addzero.addl.ai.util.ai.ctx

import cn.hutool.core.util.NumberUtil
import cn.hutool.core.util.ReflectUtil
import cn.hutool.core.util.StrUtil
import cn.hutool.extra.spring.SpringUtil
import com.addzero.addl.ai.consts.ChatModels.DASH_SCOPE
import com.addzero.addl.ai.consts.ChatModels.OLLAMA
import com.addzero.addl.ai.consts.ChatModels.QWEN_MAX
import com.addzero.addl.ktututil.toJson
import com.addzero.addl.settings.SettingContext
import com.addzero.ai.modules.ai.consts.Promts
import com.addzero.ai.modules.ai.util.ai.ai_abs_builder.AiUtil.Companion.buildStructureOutPutPrompt
import com.addzero.common.kt_util.addPrefixIfNot
import com.addzero.common.kt_util.cleanBlank
import com.addzero.common.kt_util.isBlank
import com.addzero.common.kt_util.isNotBlank
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.ai.ollama.api.OllamaApi
import org.springframework.ai.ollama.api.OllamaOptions


fun ChatClient.Builder.defopt(modelName: String, chatOptions: ChatOptions): ChatClient? {

    val defaultAdvisors = this.defaultAdvisors(
        SimpleLoggerAdvisor({ request: AdvisedRequest ->
            val userText = request.userText()
            """
                        "request: " + $userText
                        "Custom model: " + $modelName
                        """.trimIndent()
        }, { response: ChatResponse -> "Custom response: " + response.result })
    )
    return defaultAdvisors.build()
//    val defaultOptions = defaultAdvisors.defaultOptions(chatOptions)
//    val build = defaultOptions.build()
//    return build

}


object AiCtx {
    fun dajsd(): Unit {
        val pairs = "dasd"
        mapOf(QWEN_MAX to pairs)

    }


    /**
     * 结构化输出上下文
     * @param [question] 问题
     * @param [promptTemplate] 提示模板
     * @param [clazz] 提供类以自动生成 formatJson 和 jsonComment（可选）
     * @return [StructureOutPutPrompt]
     */
    fun structuredOutputContext(
        question: String,
        promptTemplate: String,
        clazz: Class<*>,
    ): StructureOutPutPrompt {

        val finalFormatJson = ReflectUtil.newInstance(clazz).toJson()
        val finalJsonComment = buildStructureOutPutPrompt(clazz)
        // 生成结构化输出的内容
        return structuredOutputContext(
            question.cleanBlank(), promptTemplate.cleanBlank(), finalFormatJson, finalJsonComment
        )
    }


    fun structuredOutputContext(
        question: String,
        promptTemplate: String?,
        formatJson: String?,
        jsonComment: String?,
    ): StructureOutPutPrompt {


        val que = "{question}"
        if (promptTemplate.isBlank() && formatJson.isBlank()) {
            val promptTemplate1 = promptTemplate.cleanBlank()
            val promptTemplate2 = StrUtil.addPrefixIfNot(promptTemplate1, que)
            return StructureOutPutPrompt(promptTemplate2, mapOf("question" to question))
        }
        if (promptTemplate.isBlank() && formatJson.isNotBlank()) {
            val promptTemplate1 = promptTemplate.cleanBlank()
            val promptTemplate2 = StrUtil.addPrefixIfNot(promptTemplate1, que)
            return StructureOutPutPrompt(promptTemplate2, mapOf("question" to question))
        }

        val formatPrompt: String = """
       ${Promts.JSON_PATTERN_PROMPT}
           {formatJson}}
            ------------------
           以下是json数据的注释：
           {jsonComment}
        """.trimIndent()
        val newPromptTemplate = promptTemplate + formatPrompt
        val quesCtx = mapOf(
            "question" to question, "formatJson" to formatJson, "jsonComment" to jsonComment
        )
        return StructureOutPutPrompt(newPromptTemplate.addPrefixIfNot(que), quesCtx)
    }

    data class StructureOutPutPrompt(val newPrompt: String, val quesCtx: Map<String, String?>)


    /**
     *
     *  #  * * 0 = "dashScopeAiVLChatModel"
     *   #  * * 1 = "dashScopeAiChatModel"
     *   #  * * 2 = "moonshotChatModel"
     *   #  * * 3 = "ollamaChatModel"        should be a list in  spring yml ?
     *   #  *         ------qwen2.5:1.5b
     *   #  *         ------qwen2.5-coder:1.5b
     *
     *   #  * * 4 = "openAiChatModel"
     *   #  * * 5 = "zhiPuAiChatModel"
     *
     *
     *
     *If the user-defined model name cannot be found, go to the ollama model to find it
     *  @param [modelName]
     * @return [ChatClient?]
     */
    fun defaultChatClient(modelName: String): ChatClient {
        val settings = SettingContext.settings
        val modelManufacturer = settings.modelManufacturer
        val modelNameOnline = settings.modelNameOnline
        val modelNameOffline = settings.modelNameOffline
        val defaultChatModel = defaultChatModel(modelManufacturer)

//        const val OLLAMA =  "ollamaChatModel"
//        const val KIMI_MOONSHOT = "moonshotChatModel"
//        const val OPENAI = "openAiChatModel"
//        const val ZHIPU = "zhiPuAiChatModel"

        var buildOpt = defaultChatModel.defaultOptions
        val defaultChatMode2 = if (modelManufacturer == DASH_SCOPE) {
            getSettingDashScopeModel(modelNameOnline, buildOpt)
        } else if (modelManufacturer == OLLAMA) {
            getSettingOllamaModel(modelNameOffline)
//        } else if (modelManufacturer == KIMI_MOONSHOT) {
//            getSettingKimiModel(modelName)
//        } else if (modelManufacturer == OPENAI) {
//            getSettingOpenAiModel(modelName)
//        } else if (modelManufacturer == ZHIPU) {
//            getSettingZhipuModel(modelName)
        } else {
            null
        } as ChatModel

        val chatClient = ChatClient.builder(defaultChatMode2).defopt(modelName, buildOpt)!!
        return chatClient
    }

    private fun getSettingOllamaModel(modelName: String): OllamaChatModel {
        val settings = SettingContext.settings
        // 修改设置项
        val ollamaUrl = settings.ollamaUrl
        val temPerature = settings.temPerature
        val ollamaApi = OllamaApi(ollamaUrl)
        val chatModel = OllamaChatModel(
            ollamaApi, OllamaOptions.create().withModel(modelName).withTemperature(NumberUtil.parseDouble(temPerature))
        )
        return chatModel
    }

    private fun getSettingDashScopeModel(modelName: String, buildOpt: ChatOptions): DashScopeChatModel {
        val settings = SettingContext.settings
        // 修改设置项
        val modelName = settings.modelNameOnline
        val temPerature = settings.temPerature
        val modelKey = settings.modelKey
        val api = DashScopeApi(modelKey)
        val withTemperature =
            DashScopeChatOptions.builder().withModel(modelName).withTemperature(NumberUtil.parseFloat(temPerature))
        val build = withTemperature.build()
        val chatModel = DashScopeChatModel(
            api, build
        )
        return chatModel
    }

//    private fun getSettingZhipuModel(modelName: String): ZhiPuAiChatModel {
//        val settings = SettingContext.settings
//        // 修改设置项
//        val modelName = settings.modelNameOnline
//        val temPerature = settings.temPerature
//        val modelKey = settings.modelKey
//        val api = ZhiPuAiApi(modelKey)
//        val parseDouble = NumberUtil.parseDouble(temPerature)
//        val chatModel = ZhiPuAiChatModel(
//            api,
//            ZhiPuAiChatOptions.builder().withModel(ZhiPuAiApi.ChatModel.GLM_3_Turbo.getValue())
//                .withTemperature(parseDouble).withMaxTokens(200).build()
//        )
//        return chatModel
//    }
//
//    private fun getSettingOpenAiModel(modelName: String): OpenAiChatModel {
//        val settings = SettingContext.settings
//        val temPerature = settings.temPerature
//        val modelKey = settings.modelKey
//        val api = OpenAiApi(modelKey)
//
//        val parseDouble = NumberUtil.parseDouble(temPerature)
//        val openAiApi = OpenAiApi(modelKey)
//        val openAiChatOptions =
//            OpenAiChatOptions.builder().withModel("gpt-3.5-turbo").withTemperature(parseDouble).withMaxTokens(200)
//                .build()
//
//        val chatModel = OpenAiChatModel(openAiApi, openAiChatOptions)
//        return chatModel
//    }
//
//
//    private fun getSettingKimiModel(modelName: String): MoonshotChatModel {
//        val settings = SettingContext.settings
//        val temPerature = settings.temPerature
//        val modelKey = settings.modelKey
//        val moonshotApi = MoonshotApi(modelKey)
//        val chatModel = MoonshotChatModel(
//            moonshotApi,
//            MoonshotChatOptions.builder().withModel(MoonshotApi.ChatModel.MOONSHOT_V1_8K.value).withTemperature(0.4)
//                .withMaxTokens(200).build()
//        )
//        return chatModel
//    }


    private fun defaultChatModel(modelName: String): ChatModel {
        val bean = SpringUtil.getBeansOfType<ChatModel>(ChatModel::class.java)
        val model: ChatModel = bean[modelName] ?: SpringUtil.getBean<OllamaChatModel>(OllamaChatModel::class.java)
        return model
    }


    fun withMemory(chatClient: ChatClient.Builder): ChatClient {
        val chatMemory = SpringUtil.getBean(ChatMemory::class.java)
        val promptChatMemoryAdvisor = PromptChatMemoryAdvisor(chatMemory)
        return chatClient.defaultAdvisors(
            promptChatMemoryAdvisor
        ).build()
    }


}
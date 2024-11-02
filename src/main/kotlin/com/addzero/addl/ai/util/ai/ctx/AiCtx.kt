package com.addzero.addl.ai.util.ai.ctx

import cn.hutool.core.util.ReflectUtil
import cn.hutool.core.util.StrUtil
import com.addzero.addl.ai.util.ai.AiUtil.Companion.buildStructureOutPutPrompt
import com.addzero.addl.ktututil.toJson
import com.addzero.ai.modules.ai.consts.Promts
import com.addzero.common.kt_util.addPrefixIfNot
import com.addzero.common.kt_util.cleanBlank
import com.addzero.common.kt_util.isBlank
import com.addzero.common.kt_util.isNotBlank


object AiCtx {

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


}
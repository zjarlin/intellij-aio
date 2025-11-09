package com.addzero.util.lsi_impl.impl.kt.`fun`

import com.addzero.util.lsi_impl.impl.kt.anno.getArg
import org.jetbrains.kotlin.psi.KtFunction
import site.addzero.util.str.cleanDocComment
import site.addzero.util.str.removeAnyQuote

fun KtFunction.getComment(): String? {
    // 尝试从注解中获取描述
    this.annotationEntries.forEach { annotation ->
        val shortName = annotation.shortName?.asString()
        val description = when (shortName) {
            "ApiModelProperty" -> {
                // 获取第一个参数（value）
                val arg = annotation.getArg()
                arg
            }
            "Schema" -> {
                val arg = annotation.getArg("description")
                arg
            }
            else -> null
        }
        if (!description.isNullOrBlank()) {
            return description.removeAnyQuote()
        }
    }

    return cleanDocComment(this.docComment?.text)
}

package com.addzero.util.lsi_impl.impl.kt.`fun`

import com.addzero.util.lsi_impl.impl.kt.KtMethodAnalyzers.CommentAnalyzer.cleanDocComment
import com.addzero.util.lsi_impl.impl.kt.KtMethodAnalyzers.CommentAnalyzer.cleanQuotes
import org.jetbrains.kotlin.psi.KtFunction

fun KtFunction.getComment(): String? {
            // 尝试从注解中获取描述
            this.annotationEntries.forEach { annotation ->
                val shortName = annotation.shortName?.asString()
                val description = when (shortName) {
                    "ApiModelProperty" -> {
                        // 获取第一个参数（value）
                        annotation.valueArguments.firstOrNull()
                            ?.getArgumentExpression()?.text
                    }
                    "Schema" -> {
                        // 获取 description 参数
                        annotation.valueArguments
                            .find { it.getArgumentName()?.asName?.asString() == "description" }
                            ?.getArgumentExpression()?.text
                    }
                    else -> null
                }

                if (!description.isNullOrBlank()) {
                    return cleanQuotes(description)
                }
            }

            // 如果注解中没有，则返回清理后的文档注释
            return cleanDocComment(this.docComment?.text)
        }

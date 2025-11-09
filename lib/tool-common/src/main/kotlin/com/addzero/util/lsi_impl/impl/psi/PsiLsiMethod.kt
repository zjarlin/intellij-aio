package com.addzero.util.lsi_impl.impl.psi

import com.addzero.util.lsi.*
import com.addzero.util.lsi.anno.LsiAnnotation
import com.addzero.util.lsi.clazz.LsiClass
import com.addzero.util.lsi.method.LsiMethod
import com.addzero.util.lsi.method.LsiParameter
import com.addzero.util.lsi.type.LsiType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiParameter

/**
 * 基于 PSI 的 LsiMethod 实现
 */
class PsiLsiMethod(private val psiMethod: PsiMethod) : LsiMethod {
    override val name: String?
        get() = psiMethod.name

    override val returnType: LsiType?
        get() = psiMethod.returnType?.let { PsiLsiType(it) }

    override val returnTypeName: String?
        get() = psiMethod.returnType?.presentableText

    override val comment: String?
        get() = PsiMethodAnalyzers.CommentAnalyzer.getComment(psiMethod)

    override val annotations: List<LsiAnnotation>
        get() = psiMethod.annotations.map { PsiLsiAnnotation(it) }

    override val isStatic: Boolean
        get() = psiMethod.hasModifierProperty(PsiModifier.STATIC)

    override val isAbstract: Boolean
        get() = psiMethod.hasModifierProperty(PsiModifier.ABSTRACT)

    override val parameters: List<LsiParameter>
        get() = psiMethod.parameterList.parameters.map { PsiLsiParameter(it) }

    override val declaringClass: LsiClass?
        get() = psiMethod.containingClass?.let { PsiLsiClass(it) }
}

/**
 * 基于 PSI 的 LsiParameter 实现
 */
class PsiLsiParameter(private val psiParameter: PsiParameter) : LsiParameter {
    override val name: String?
        get() = psiParameter.name

    override val type: LsiType?
        get() = PsiLsiType(psiParameter.type)

    override val typeName: String?
        get() = psiParameter.type.presentableText

    override val annotations: List<LsiAnnotation>
        get() = psiParameter.annotations.map { PsiLsiAnnotation(it) }
}

/**
 * PSI 方法分析器集合
 */
object PsiMethodAnalyzers {
    /**
     * 注释分析器 - 从注解或文档注释中提取方法描述
     */
    object CommentAnalyzer {
        private const val API_MODEL_PROPERTY = "io.swagger.annotations.ApiModelProperty"
        private const val SCHEMA = "io.swagger.v3.oas.annotations.media.Schema"

        fun getComment(psiMethod: PsiMethod): String? {
            // 尝试从注解中获取描述
            psiMethod.annotations.forEach { annotation ->
                val qualifiedName = annotation.qualifiedName
                val description = when (qualifiedName) {
                    API_MODEL_PROPERTY -> {
                        annotation.findAttributeValue("value")?.text
                    }
                    SCHEMA -> {
                        annotation.findAttributeValue("description")?.text
                    }
                    else -> null
                }

                if (!description.isNullOrBlank()) {
                    return cleanQuotes(description)
                }
            }

            // 如果注解中没有，则返回清理后的文档注释
            return cleanDocComment(psiMethod.docComment?.text)
        }

        private fun cleanQuotes(text: String): String {
            return text.trim().removeSurrounding("\"")
        }

        private fun cleanDocComment(docComment: String?): String? {
            if (docComment == null) return null

            val cleaned = docComment
                .replace(Regex("""/\*\*?"""), "")  // 去除开头的 /* 或 /**
                .replace(Regex("""\*"""), "")      // 去除行内的 *
                .replace(Regex("""\*/"""), "")     // 去除结尾的 */
                .replace(Regex("""/"""), "")       // 去除结尾的 /
                .replace(Regex("""\n"""), " ")     // 将换行替换为空格
                .replace(Regex("""\s+"""), " ")    // 合并多个空格为一个
                .trim()

            return if (cleaned.isBlank()) null else cleaned
        }
    }
}

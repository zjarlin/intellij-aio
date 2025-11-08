package com.addzero.util.lsi.impl.psi

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiClassType
import com.intellij.psi.util.InheritanceUtil

/**
 * Java字段分析器集合
 */
object PsiFieldAnalyzers {

    /**
     * 静态字段分析器
     */
    object StaticFieldAnalyzer {
        fun isStaticField(psiField: PsiField): Boolean {
            return psiField.hasModifierProperty(PsiModifier.STATIC)
        }
    }

    /**
     * 集合类型分析器
     */
    object CollectionTypeAnalyzer {
        fun isCollectionType(psiField: PsiField): Boolean {
            return isCollectionType(psiField.type)
        }

        fun isCollectionType(psiType: PsiType): Boolean {
            if (psiType !is PsiClassType) return false
            val qualifiedName = psiType.resolve()?.qualifiedName ?: return false

            val COLLECTION_TYPE_FQ_NAMES = setOf(
                "java.util.Collection", "java.util.List", "java.util.Set", "java.util.Map",
                "kotlin.collections.Collection", "kotlin.collections.List", "kotlin.collections.Set",
                "kotlin.collections.Map", "kotlin.collections.ArrayList", "kotlin.collections.LinkedList",
                "kotlin.collections.HashSet", "kotlin.collections.LinkedHashSet",
                "kotlin.collections.HashMap", "kotlin.collections.LinkedHashMap"
            )

            return when {
                InheritanceUtil.isInheritor(psiType, CommonClassNames.JAVA_UTIL_COLLECTION) -> true
                InheritanceUtil.isInheritor(psiType, CommonClassNames.JAVA_UTIL_MAP) -> true
                qualifiedName in COLLECTION_TYPE_FQ_NAMES -> true
                qualifiedName.startsWith("kotlin.collections.") -> {
                    qualifiedName.substringAfterLast(".") in setOf(
                        "List", "MutableList", "Set", "MutableSet", "Map", "MutableMap",
                        "Collection", "MutableCollection", "ArrayList", "LinkedHashSet",
                        "HashSet", "LinkedHashMap", "HashMap"
                    )
                }
                else -> false
            }
        }
    }

    /**
     * 常量字段分析器
     */
    object ConstantFieldAnalyzer {
        fun isConstantField(psiField: PsiField): Boolean {
            return psiField.hasModifierProperty(PsiModifier.FINAL) &&
                    psiField.hasModifierProperty(PsiModifier.STATIC)
        }
    }

    /**
     * 列名分析器
     */
    object ColumnNameAnalyzer {
        private const val JIMMER_COLUMN = "org.babyfish.jimmer.sql.Column"
        private const val MP_TABLE_FIELD = "com.baomidou.mybatisplus.annotation.TableField"

        fun getColumnName(psiField: PsiField): String? {
            psiField.annotations.forEach { annotation ->
                when (annotation.qualifiedName) {
                    JIMMER_COLUMN -> {
                        val nameValue = annotation.findAttributeValue("name")
                        if (nameValue != null) {
                            return nameValue.text?.trim('"')
                        }
                    }
                    MP_TABLE_FIELD -> {
                        val valueAttr = annotation.findAttributeValue("value")
                        if (valueAttr != null) {
                            return valueAttr.text?.trim('"')
                        }
                    }
                }
            }
            return null
        }
    }

    /**
     * 注释分析器 - 从注解或文档注释中提取字段描述
     */
    object CommentAnalyzer {
        private const val API_MODEL_PROPERTY = "io.swagger.annotations.ApiModelProperty"
        private const val SCHEMA = "io.swagger.v3.oas.annotations.media.Schema"
        private const val EXCEL_PROPERTY_ALIBABA = "com.alibaba.excel.annotation.ExcelProperty"
        private const val EXCEL_PROPERTY_IDEV = "cn.idev.excel.annotation.ExcelProperty"
        private const val EXCEL_EASYPOI = "cn.afterturn.easypoi.excel.annotation.Excel"

        fun getComment(psiField: PsiField): String? {
            // 首先尝试从注解中获取描述
            psiField.annotations.forEach { annotation ->
                val description = when (annotation.qualifiedName) {
                    API_MODEL_PROPERTY -> {
                        annotation.findAttributeValue("value")?.text
                    }
                    SCHEMA -> {
                        annotation.findAttributeValue("description")?.text
                    }
                    EXCEL_PROPERTY_ALIBABA, EXCEL_PROPERTY_IDEV -> {
                        annotation.findAttributeValue("value")?.text
                    }
                    EXCEL_EASYPOI -> {
                        annotation.findAttributeValue("name")?.text
                    }
                    else -> null
                }

                if (!description.isNullOrBlank()) {
                    return cleanQuotes(description)
                }
            }

            // 如果注解中没有，则返回清理后的文档注释
            return cleanDocComment(psiField.docComment?.text)
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

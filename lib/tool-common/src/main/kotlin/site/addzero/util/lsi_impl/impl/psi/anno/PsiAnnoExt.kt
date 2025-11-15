package site.addzero.util.lsi_impl.impl.psi.anno

import site.addzero.util.lsi.constant.*
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiLiteralValue
import site.addzero.util.str.removeAnyQuote

// ============ 基础注解值提取 ============

/**
 * 获取 PsiAnnotation 的属性值（别名：getAttributeValue）
 *
 * @param argName 属性名称，如 "value", "description" 等
 * @return 属性值的文本表示（未处理引号），如果不存在则返回 null
 */
fun PsiAnnotation.getArg(argName: String): String? {
    val text1 = this.findAttributeValue(argName)?.text
    return text1?.trim('"')
}

/**
 * 获取 PsiAnnotation 的默认属性值（value）
 */
fun PsiAnnotation.getArg(): String? {
    return getArg("value")
}

/**
 * 获取 PsiAnnotation 的属性值并移除引号
 *
 * @param attributeName 属性名称
 * @return 属性值（已移除引号），如果不存在则返回空字符串
 */
fun PsiAnnotation.getAttributeValueUnquoted(attributeName: String): String {
    val value = findAttributeValue(attributeName)
    return when (value) {
        is PsiLiteralValue -> value.value?.toString() ?: ""
        else -> value?.text?.removeSurrounding("\"") ?: ""
    }
}

/**
 * 获取 PsiAnnotation 数组类型属性的所有值
 *
 * @param attributeName 属性名称
 * @return 属性值列表
 */
fun PsiAnnotation.getAttributeValueArray(attributeName: String): List<String> {
    val attributeValue = findAttributeValue(attributeName) ?: return emptyList()

    return when (attributeValue) {
        is PsiArrayInitializerMemberValue -> {
            attributeValue.initializers.mapNotNull {
                when (it) {
                    is PsiLiteralValue -> it.value?.toString()
                    else -> it.text?.removeSurrounding("\"")
                }
            }
        }
        is PsiLiteralValue -> listOf(attributeValue.value?.toString() ?: "")
        else -> listOf(attributeValue.text?.removeSurrounding("\"") ?: "")
    }
}

// ============ 注解检查 ============

/**
 * 检查注解是否包含指定的属性
 *
 * @param attributeName 属性名称
 * @return 如果包含该属性返回 true，否则返回 false
 */
fun PsiAnnotation.hasAttribute(attributeName: String): Boolean {
    return findAttributeValue(attributeName) != null
}

/**
 * 检查注解的简单名称是否在指定的列表中
 *
 * @param shortNames 简单名称列表，如 ["Schema", "ApiModelProperty"]
 * @return 如果匹配返回 true，否则返回 false
 */
fun PsiAnnotation.hasShortName(vararg shortNames: String): Boolean {
    val shortName = qualifiedName?.substringAfterLast('.') ?: return false
    return shortName in shortNames
}

/**
 * 检查注解的全限定名是否在指定的列表中
 *
 * @param qualifiedNames 全限定名列表
 * @return 如果匹配返回 true，否则返回 false
 */
fun PsiAnnotation.hasQualifiedName(vararg qualifiedNames: String): Boolean {
    return qualifiedName in qualifiedNames
}

/**
 * 获取注解上指定简单名称的所有注解
 *
 * @param shortNames 简单名称列表
 * @return 匹配的注解列表
 */
fun PsiAnnotation.getAnnotationsByShortName(vararg shortNames: String): List<PsiAnnotation> {
    // This function is for arrays of annotations, not single annotations
    // Return single item list if matches
    val shortName = qualifiedName?.substringAfterLast('.') ?: return emptyList()
    return if (shortName in shortNames) listOf(this) else emptyList()
}

// ============ 字段注释推断 ============

/**
 * 从注解数组中猜测字段注释
 * 按优先级检查常用注解（ApiModelProperty, Schema, ExcelProperty, Excel）
 *
 * @return 推断的字段注释，如果没有找到则返回 null
 */
fun Array<out PsiAnnotation>.guessFieldCommentOrNull(): String? {
    this.forEach { annotation ->
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
            return description.removeAnyQuote()
        }
    }

    return null
}

package site.addzero.util.lsi_impl.impl.kt.anno

import org.jetbrains.kotlin.asJava.toLightAnnotation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*

// ============ 属性访问 ============

/**
 * 获取KtAnnotationEntry对应注解的全限定名
 */
val KtAnnotationEntry.qualifiedName: String
    get() {
        val shortName = typeReference?.text ?: return ""
        // 解析为 FqName（需要解析导入）
        return FqName(shortName).asString()
    }

val KtAnnotationEntry.simplaName: String?
    get() {
        val asString = this.shortName?.asString()
        return asString
    }

// ============ 基础注解值提取 ============

/**
 * 获取 KtAnnotationEntry 的属性值（别名：getAttributeValue）
 *
 * @param argumentName 属性名称，如 "value", "description" 等
 * @return 属性值的文本表示（已移除引号），如果不存在则返回 null
 */
fun KtAnnotationEntry.getArg(argumentName: String): String? {
    // 尝试获取指定参数
    val trim = this.valueArguments.find {
        it.getArgumentName()?.asName?.asString() == argumentName
    }?.getArgumentExpression()?.text?.trim('"')
    return trim
}

/**
 * 获取 KtAnnotationEntry 的默认属性值（value）
 */
fun KtAnnotationEntry.getArg(): String? {
    return getArg("value")
}

/**
 * 获取 KtAnnotationEntry 的属性值（完整版）
 *
 * @param attributeName 属性名称
 * @return 属性值文本（已移除引号），如果不存在则返回 null
 */
fun KtAnnotationEntry.getAttributeValue(attributeName: String): String? {
    return valueArguments.find {
        it.getArgumentName()?.asName?.asString() == attributeName
    }?.getArgumentExpression()?.let { expr ->
        when (expr) {
            is KtConstantExpression -> expr.text
            is KtStringTemplateExpression -> expr.text
            else -> null
        }
    }?.removeSurrounding("\"")
}

/**
 * 获取 KtAnnotationEntry 的属性值原始文本（保留引号）
 *
 * @param attributeName 属性名称
 * @return 属性值原始文本，如果不存在则返回 null
 */
fun KtAnnotationEntry.getAttributeValueRaw(attributeName: String): String? {
    return valueArguments.find {
        it.getArgumentName()?.asName?.asString() == attributeName
    }?.getArgumentExpression()?.text
}

/**
 * 获取 KtAnnotationEntry 数组类型属性的所有值
 *
 * @param attributeName 属性名称
 * @return 属性值列表
 */
fun KtAnnotationEntry.getAttributeValueArray(attributeName: String): List<String> {
    val argumentExpression = valueArguments.find {
        it.getArgumentName()?.asName?.asString() == attributeName
    }?.getArgumentExpression()

    return when (argumentExpression) {
        is KtCollectionLiteralExpression -> {
            argumentExpression.getInnerExpressions().mapNotNull { expr ->
                when (expr) {
                    is KtConstantExpression -> expr.text.removeSurrounding("\"")
                    is KtStringTemplateExpression -> expr.text.removeSurrounding("\"")
                    else -> null
                }
            }
        }
        is KtConstantExpression -> listOf(argumentExpression.text.removeSurrounding("\""))
        is KtStringTemplateExpression -> listOf(argumentExpression.text.removeSurrounding("\""))
        else -> emptyList()
    }
}

// ============ 注解检查 ============

/**
 * 检查注解是否包含指定的属性
 *
 * @param attributeName 属性名称
 * @return 如果包含该属性返回 true，否则返回 false
 */
fun KtAnnotationEntry.hasAttribute(attributeName: String): Boolean {
    return valueArguments.any {
        it.getArgumentName()?.asName?.asString() == attributeName
    }
}

/**
 * 检查注解的简单名称是否在指定的列表中
 *
 * @param shortNames 简单名称列表，如 ["Schema", "ApiModelProperty"]
 * @return 如果匹配返回 true，否则返回 false
 */
fun KtAnnotationEntry.hasShortName(vararg shortNames: String): Boolean {
    val shortName = shortName?.asString() ?: return false
    return shortName in shortNames
}

/**
 * 检查注解的全限定名是否在指定的列表中
 * 需要通过 toLightAnnotation 转换获取全限定名
 *
 * @param qualifiedNames 全限定名列表
 * @return 如果匹配返回 true，否则返回 false
 */
fun KtAnnotationEntry.hasQualifiedName(vararg qualifiedNames: String): Boolean {
    val qualifiedName = toLightAnnotation()?.qualifiedName ?: return false
    return qualifiedName in qualifiedNames
}

/**
 * 获取注解的简单名称
 *
 * @return 简单名称字符串，如果不存在则返回 null
 */
fun KtAnnotationEntry.getShortName(): String? {
    return shortName?.asString()
}

/**
 * 获取注解的全限定名
 * 通过转换为 Light Annotation 获取
 *
 * @return 全限定名字符串，如果不存在则返回 null
 */
fun KtAnnotationEntry.getQualifiedName(): String? {
    return toLightAnnotation()?.qualifiedName
}

// ============ 注解列表工具 ============

/**
 * 从注解列表中查找指定简单名称的注解
 *
 * @param simpleName 注解简单名称
 * @return 找到的注解，如果不存在则返回 null
 */
fun List<KtAnnotationEntry>.getAnno(simpleName: String): KtAnnotationEntry? {
    val firstOrNull = this.firstOrNull { it.shortName?.asString() == simpleName }
    return firstOrNull
}

/**
 * 从注解列表中获取指定注解的指定参数值
 *
 * @param simpleName 注解简单名称
 * @param argumentName 参数名称
 * @return 参数值，如果不存在则返回 null
 */
fun List<KtAnnotationEntry>.getArg(simpleName: String, argumentName: String): String? {
    val anno = getAnno(simpleName)
    val arg = anno?.getArg(argumentName)
    return arg
}




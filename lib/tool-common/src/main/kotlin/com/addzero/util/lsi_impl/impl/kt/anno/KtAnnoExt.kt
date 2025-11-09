package com.addzero.util.lsi_impl.impl.kt.anno

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry

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


fun KtAnnotationEntry.getArg(argumentName: String): String? {
    // 尝试获取 name 参数
    val trim = this.valueArguments.find { it.getArgumentName()?.asName?.asString() == argumentName }?.getArgumentExpression()?.text?.trim('"')
    return trim
}

fun List<KtAnnotationEntry>.getAnno(simpleName: String): KtAnnotationEntry? {
    val firstOrNull = this.firstOrNull { it.shortName?.asString() == simpleName }
    return firstOrNull
}

fun List<KtAnnotationEntry>.getArg(simpleName: String, argumentName: String): String? {
    val anno = getAnno(simpleName)
    val arg = anno?.getArg(argumentName)
    return arg
}




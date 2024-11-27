package com.addzero.addl.util

import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * 判断 KtProperty 是否为静态字段
 */
fun isStaticField(ktProperty: KtProperty): Boolean {
    // 检查是否有 const 修饰符
    if (ktProperty.hasModifier(KtTokens.CONST_KEYWORD)) {
        return true
    }

    // 检查是否是伴生对象中的属性
    val isInCompanionObject = ktProperty.getParentOfType<KtObjectDeclaration>(true)?.isCompanion() == true

    // 检查是否是对象声明中的属性
    val isInObject = ktProperty.getParentOfType<KtObjectDeclaration>(true) != null

    // 检查是否有 @JvmStatic 注解
    val hasJvmStatic = ktProperty.annotationEntries.any {
        it.shortName?.asString() == "JvmStatic"
    }

    return when {
        // const 属性一定是静态的
        ktProperty.hasModifier(KtTokens.CONST_KEYWORD) -> true

        // 伴生对象中的 @JvmStatic 属性是静态的
        isInCompanionObject && hasJvmStatic -> true

        // 对象声明中的 @JvmStatic 属性是静态的
        isInObject && hasJvmStatic -> true

        // 顶层属性是静态的
        ktProperty.isTopLevel -> true

        else -> false
    }
}

/**
 * 更详细的版本，包含静态字段的具体类型
 */
sealed class StaticFieldType {
    object NotStatic : StaticFieldType()
    object Const : StaticFieldType()
    object CompanionObject : StaticFieldType()
    object ObjectDeclaration : StaticFieldType()
    object TopLevel : StaticFieldType()
    object JvmStatic : StaticFieldType()
}

fun getStaticFieldType(ktProperty: KtProperty): StaticFieldType {
    return when {
        ktProperty.hasModifier(KtTokens.CONST_KEYWORD) ->
            StaticFieldType.Const

        ktProperty.getParentOfType<KtObjectDeclaration>(true)?.isCompanion() == true -> {
            if (ktProperty.hasAnnotation("kotlin.jvm.JvmStatic")) {
                StaticFieldType.JvmStatic
            } else {
                StaticFieldType.CompanionObject
            }
        }

        ktProperty.getParentOfType<KtObjectDeclaration>(true) != null -> {
            if (ktProperty.hasAnnotation("kotlin.jvm.JvmStatic")) {
                StaticFieldType.JvmStatic
            } else {
                StaticFieldType.ObjectDeclaration
            }
        }

        ktProperty.isTopLevel ->
            StaticFieldType.TopLevel

        else ->
            StaticFieldType.NotStatic
    }
}

/**
 * 扩展函数：检查是否有指定注解
 */
fun KtModifierListOwner.hasAnnotation(fqName: String): Boolean {
    return annotationEntries.any {
        it.shortName?.asString() == fqName.substringAfterLast('.')
    }
}
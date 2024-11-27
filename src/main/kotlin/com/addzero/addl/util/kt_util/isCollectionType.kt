package com.addzero.addl.util.kt_util

import com.addzero.common.kt_util.isNotNull
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.lexer.KtTokens
import com.intellij.psi.PsiType
import com.intellij.psi.PsiClassType
import com.intellij.psi.CommonClassNames
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * 扩展函数：判断 PsiType 是否为集合类型
 */
fun PsiType.isCollectionType(): Boolean {
    if (this !is PsiClassType) return false
    val qualifiedName = resolve()?.qualifiedName ?: return false

    return when {
        InheritanceUtil.isInheritor(this, CommonClassNames.JAVA_UTIL_COLLECTION) -> true
        InheritanceUtil.isInheritor(this, CommonClassNames.JAVA_UTIL_MAP) -> true
        qualifiedName in COLLECTION_TYPE_FQ_NAMES -> true
        qualifiedName.startsWith("kotlin.collections.") -> {
            qualifiedName.substringAfterLast(".") in KOTLIN_COLLECTION_SIMPLE_NAMES
        }
        else -> false
    }
}

/**
 * 扩展函数：判断 PsiType 是否为可空集合类型
 */
fun PsiType.isNullableCollectionType(): Boolean =
    this.isCollectionType() && !this.isNotNull()

/**
 * 扩展函数：获取集合的元素类型
 */
fun PsiType.getCollectionElementType(): PsiType? {
    if (!this.isCollectionType()) return null
    return (this as? PsiClassType)?.parameters?.firstOrNull()
}

/**
 * 扩展函数：获取 Map 的键值类型
 */
fun PsiType.getMapKeyValueTypes(): Pair<PsiType?, PsiType?>? {
    if (this !is PsiClassType || !this.isMapType()) return null
    val parameters = this.parameters
    return if (parameters.size >= 2) {
        parameters[0] to parameters[1]
    } else null
}

/**
 * 扩展函数：判断是否为 Map 类型
 */
fun PsiType.isMapType(): Boolean {
    return this is PsiClassType &&
           InheritanceUtil.isInheritor(this, CommonClassNames.JAVA_UTIL_MAP)
}

/**
 * 扩展函数：判断 KtProperty 是否为静态字段
 */
fun KtProperty.isStatic(): Boolean {
    return when {
        this.hasModifier(KtTokens.CONST_KEYWORD) -> true
        this.isInCompanionObject() && this.hasJvmStaticAnnotation() -> true
        this.isInObjectDeclaration() && this.hasJvmStaticAnnotation() -> true
        this.isTopLevel -> true
        else -> false
    }
}

/**
 * 扩展函数：判断属性是否在伴生对象中
 */
fun KtProperty.isInCompanionObject(): Boolean =
    this.getParentOfType<KtObjectDeclaration>(true)?.isCompanion() == true

/**
 * 扩展函数：判断属性是否在对象声明中
 */
fun KtProperty.isInObjectDeclaration(): Boolean =
    this.getParentOfType<KtObjectDeclaration>(true) != null

/**
 * 扩展函数：判断是否有 @JvmStatic 注解
 */
fun KtProperty.hasJvmStaticAnnotation(): Boolean =
    this.annotationEntries.any { it.shortName?.asString() == "JvmStatic" }

/**
 * 扩展函数：获取静态字段类型
 */
fun KtProperty.getStaticFieldType(): StaticFieldType {
    return when {
        this.hasModifier(KtTokens.CONST_KEYWORD) ->
            StaticFieldType.Const

        this.isInCompanionObject() -> {
            if (this.hasJvmStaticAnnotation()) {
                StaticFieldType.JvmStatic
            } else {
                StaticFieldType.CompanionObject
            }
        }

        this.isInObjectDeclaration() -> {
            if (this.hasJvmStaticAnnotation()) {
                StaticFieldType.JvmStatic
            } else {
                StaticFieldType.ObjectDeclaration
            }
        }

        this.isTopLevel ->
            StaticFieldType.TopLevel

        else ->
            StaticFieldType.NotStatic
    }
}

/**
 * 扩展函数：检查是否有指定注解
 */
fun KtAnnotated.hasAnnotation(fqName: String): Boolean =
    this.annotationEntries.any {
        it.shortName?.asString() == fqName.substringAfterLast('.')
    }

/**
 * 扩展函数：获取注解属性值
 */
fun KtAnnotationEntry.getArgumentValue(name: String): String? {
    return valueArguments.find {
        (it.getArgumentName()?.asName?.asString() ?: "value") == name
    }?.getArgumentExpression()?.text?.trim('"')
}

// 静态字段类型枚举
sealed class StaticFieldType {
    object NotStatic : StaticFieldType()
    object Const : StaticFieldType()
    object CompanionObject : StaticFieldType()
    object ObjectDeclaration : StaticFieldType()
    object TopLevel : StaticFieldType()
    object JvmStatic : StaticFieldType()
}

// 常量定义
private val COLLECTION_TYPE_FQ_NAMES = setOf(
    CommonClassNames.JAVA_UTIL_LIST,
    CommonClassNames.JAVA_UTIL_SET,
    CommonClassNames.JAVA_UTIL_COLLECTION,
    CommonClassNames.JAVA_UTIL_MAP,
    "java.util.ArrayList",
    "java.util.LinkedList",
    "java.util.HashSet",
    "java.util.TreeSet",
    "java.util.HashMap",
    "java.util.TreeMap",
    "java.util.LinkedHashMap",
    "java.util.LinkedHashSet"
)

private val KOTLIN_COLLECTION_SIMPLE_NAMES = setOf(
    "List",
    "MutableList",
    "Set",
    "MutableSet",
    "Map",
    "MutableMap",
    "Collection",
    "MutableCollection",
    "ArrayList",
    "LinkedHashSet",
    "HashSet",
    "LinkedHashMap",
    "HashMap"
)
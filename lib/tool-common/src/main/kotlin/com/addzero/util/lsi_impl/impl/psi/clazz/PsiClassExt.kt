package com.addzero.util.lsi_impl.impl.psi.clazz

import com.addzero.util.lsi_impl.impl.kt.clazz.getArg
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import site.addzero.util.str.extractMarkdownBlockContent
import site.addzero.util.str.toUnderLineCase

/**
 * PsiClass 扩展函数集合
 */

// ============ POJO 验证相关 ============

private val ENTITY_ANNOTATIONS = setOf(
    "javax.persistence.Entity",
    "jakarta.persistence.Entity",
    "org.babyfish.jimmer.sql.Entity",
    "org.babyfish.jimmer.sql.MappedSuperclass"
)

private val TABLE_ANNOTATIONS = setOf(
    "org.babyfish.jimmer.sql.Table"
)

private val LOMBOK_ANNOTATIONS = setOf(
    "lombok.Data",
    "lombok.Getter",
    "lombok.Setter"
)

/**
 * 判断 PsiClass 是否为 POJO/实体类
 */
fun PsiClass.isPojo(): Boolean {
    // 排除接口和枚举
    if (isInterface || isEnum) {
        return false
    }

    // 排除抽象类（除非有实体注解）
    val isAbstract = hasModifierProperty(PsiModifier.ABSTRACT)

    val annotations = annotations.mapNotNull { it.qualifiedName }

    // 检查是否有实体注解
    val hasEntityAnnotation = annotations.any { it in ENTITY_ANNOTATIONS }
    val hasTableAnnotation = annotations.any { it in TABLE_ANNOTATIONS }
    val hasLombokAnnotation = annotations.any { it in LOMBOK_ANNOTATIONS }

    // 如果是抽象类，只有带实体注解才认为是 POJO
    if (isAbstract) {
        return hasEntityAnnotation || hasTableAnnotation
    }

    // 非抽象类：有任何相关注解即可
    return hasEntityAnnotation || hasTableAnnotation || hasLombokAnnotation
}

// ============ 集合类型判断相关 ============

private val COLLECTION_TYPE_FQ_NAMES = setOf(
    "java.util.Collection", "java.util.List", "java.util.Set", "java.util.Map",
    "kotlin.collections.Collection", "kotlin.collections.List", "kotlin.collections.Set",
    "kotlin.collections.Map", "kotlin.collections.ArrayList", "kotlin.collections.LinkedList",
    "kotlin.collections.HashSet", "kotlin.collections.LinkedHashSet",
    "kotlin.collections.HashMap", "kotlin.collections.LinkedHashMap"
)

/**
 * 判断 PsiClass 是否为集合类型
 */
fun PsiClass.isCollectionType(): Boolean {
    val qualifiedName = qualifiedName ?: return false

    // 检查是否为集合类型
    return COLLECTION_TYPE_FQ_NAMES.any { qualifiedName.startsWith(it) } ||
            supers.any { superClass ->
                val superQualifiedName = superClass.qualifiedName ?: return@any false
                COLLECTION_TYPE_FQ_NAMES.any { superQualifiedName.startsWith(it) }
            }
}

// ============ 表名推断相关 ============

/**
 * 推断数据库表名
 * 优先从注解获取，否则使用类名转下划线命名
 */
fun PsiClass.guessTableName(): String? {
    val text = name?.toUnderLineCase()

    // 获取所有注解
    val guessTableNameByAnno = guessTableNameByAnno()

    val firstNonBlank = cn.hutool.core.util.StrUtil.firstNonBlank(guessTableNameByAnno, text)
    return firstNonBlank
}

/**
 * 从注解中推断表名
 * 支持 MyBatis Plus、Jimmer、JPA 的表名注解
 */
fun PsiClass.guessTableNameByAnno(): String? {
    val annotations = annotations
    for (annotation in annotations) {
        val qualifiedName = annotation.qualifiedName
        when (qualifiedName) {
            "com.baomidou.mybatisplus.annotation.TableName" -> {
                // 获取 MyBatis Plus 的 @TableName 注解值
                annotation.getArg()
                val tableNameValue = annotation.findAttributeValue("value")
                return extractMarkdownBlockContent(tableNameValue?.text)
            }

            "org.babyfish.jimmer.sql.Table" -> {
                // 获取 Jimmer 的 @Table 注解值
                val nameValue = annotation.findAttributeValue("name")
                return extractMarkdownBlockContent(nameValue?.text)
            }

            "javax.persistence.Table",
            "jakarta.persistence.Table" -> {
                // 获取 JPA 的 @Table 注解值
                val nameValue = annotation.findAttributeValue("name")
                return extractMarkdownBlockContent(nameValue?.text)
            }
        }
    }
    return null
}

// ============ 方法获取相关 ============

/**
 * 获取类的所有方法（包括实体接口中的方法）
 * 如果类实现了带 @Entity 或 @MappedSuperclass 注解的接口，
 * 则也会包含这些接口中的方法
 */
fun PsiClass.methods(): List<PsiMethod> {
    val supers = interfaces.filter { interfaceClass ->
        interfaceClass.annotations.any {
            it.qualifiedName in listOf(Constant.Annotation.ENTITY, Constant.Annotation.MAPPED_SUPERCLASS)
        }
    }
    return methods.toList() + supers.map { it.methods() }.flatten()
}



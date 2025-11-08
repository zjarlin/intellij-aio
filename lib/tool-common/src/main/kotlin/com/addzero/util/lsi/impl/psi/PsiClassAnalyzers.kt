package com.addzero.util.lsi.impl.psi

import com.addzero.util.meta.Constant
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.util.PsiTreeUtil
import site.addzero.util.str.extractMarkdownBlockContent
import site.addzero.util.str.toUnderLineCase


/**
 * Java类分析器集合
 */
object PsiClassAnalyzers {

    inline fun <reified T : PsiNameIdentifierOwner> PsiFile.clazz(): T? {
        return PsiTreeUtil.findChildOfType(originalElement, T::class.java)
    }


    /**
     * 集合类型分析器
     */
    object CollectionTypeAnalyzer {
        fun isCollectionType(psiClass: PsiClass): Boolean {
            // 集合类型的全限定名列表
            val COLLECTION_TYPE_FQ_NAMES = setOf(
                "java.util.Collection", "java.util.List", "java.util.Set", "java.util.Map",
                "kotlin.collections.Collection", "kotlin.collections.List", "kotlin.collections.Set",
                "kotlin.collections.Map", "kotlin.collections.ArrayList", "kotlin.collections.LinkedList",
                "kotlin.collections.HashSet", "kotlin.collections.LinkedHashSet",
                "kotlin.collections.HashMap", "kotlin.collections.LinkedHashMap"
            )

            val qualifiedName = psiClass.qualifiedName ?: return false

            // 检查是否为集合类型
            return COLLECTION_TYPE_FQ_NAMES.any { qualifiedName.startsWith(it) } ||
                    psiClass.supers.any { superClass ->
                        val superQualifiedName = superClass.qualifiedName ?: return@any false
                        COLLECTION_TYPE_FQ_NAMES.any { superQualifiedName.startsWith(it) }
                    }
        }
    }
}

fun PsiClass.guessTableName(): String? {
    val text = this.name?.toUnderLineCase()

    // 获取所有注解
    val guessTableNameByAnno = this.guessTableNameByAnno()

    val firstNonBlank = cn.hutool.core.util.StrUtil.firstNonBlank(guessTableNameByAnno, text)
    return firstNonBlank
}

fun PsiClass.guessTableNameByAnno(): String? {
    val annotations = this.annotations
    for (annotation in annotations) {
        val qualifiedName = annotation.qualifiedName
        when (qualifiedName) {
            "com.baomidou.mybatisplus.annotation.TableName" -> {
                // 获取 MyBatis Plus 的 @TableName 注解值
                val tableNameValue = annotation.findAttributeValue("value")
                return extractMarkdownBlockContent(tableNameValue?.text)
            }

            "org.babyfish.jimmer.sql.Table" -> {
                // 获取 Jimmer 的 @Table 注解值
                val nameValue = annotation.findAttributeValue("name")
                return extractMarkdownBlockContent(nameValue?.text)

            }

            "javax.persistence.Table",
            "jakarta.persistence.Table",
                -> {
                // 获取 JPA 的 @Table 注解值
                val nameValue = annotation.findAttributeValue("name")
                return extractMarkdownBlockContent(nameValue?.text)
            }
        }
    }
    return null
}

fun PsiClass.methods(): List<PsiMethod> {
    val supers = interfaces.filter { interfaceClass ->
        interfaceClass.annotations.any {
            it.qualifiedName in listOf(Constant.Annotation.ENTITY, Constant.Annotation.MAPPED_SUPERCLASS)
        }
    }
    return methods.toList() + supers.map { it.methods() }.flatten()
}
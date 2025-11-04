package com.addzero.util.lsi.impl.kt

import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtTypeReference

/**
 * Kotlin类分析器集合
 */
object KtClassAnalyzers {
    
    /**
     * 集合类型分析器
     */
    object CollectionTypeAnalyzer {
        fun isCollectionType(ktClass: KtClass): Boolean {
            // 集合类型的全限定名列表
            val COLLECTION_TYPE_FQ_NAMES = setOf(
                "java.util.Collection", "java.util.List", "java.util.Set", "java.util.Map",
                "kotlin.collections.Collection", "kotlin.collections.List", "kotlin.collections.Set",
                "kotlin.collections.Map", "kotlin.collections.ArrayList", "kotlin.collections.LinkedList",
                "kotlin.collections.HashSet", "kotlin.collections.LinkedHashSet",
                "kotlin.collections.HashMap", "kotlin.collections.LinkedHashMap"
            )

            // 获取类的全限定名
            val fqName = ktClass.fqName?.asString() ?: return false

            // 检查是否为集合类型
            return COLLECTION_TYPE_FQ_NAMES.any { fqName.startsWith(it) }
        }
        
        fun isCollectionType(ktType: KtTypeReference): Boolean {
            // 集合类型的全限定名列表
            val COLLECTION_TYPE_FQ_NAMES = setOf(
                "java.util.Collection", "java.util.List", "java.util.Set", "java.util.Map",
                "kotlin.collections.Collection", "kotlin.collections.List", "kotlin.collections.Set",
                "kotlin.collections.Map", "kotlin.collections.ArrayList", "kotlin.collections.LinkedList",
                "kotlin.collections.HashSet", "kotlin.collections.LinkedHashSet",
                "kotlin.collections.HashMap", "kotlin.collections.LinkedHashMap"
            )

            // 获取类型的全限定名
            val fqName = ktType.name ?: return false

            // 检查是否为集合类型
            return COLLECTION_TYPE_FQ_NAMES.any { fqName.startsWith(it) }
        }
    }
}
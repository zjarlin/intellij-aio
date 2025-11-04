package com.addzero.util.lsi.impl.psi

import com.intellij.psi.PsiClass
import com.intellij.psi.CommonClassNames
import com.intellij.psi.util.InheritanceUtil

/**
 * Java类分析器集合
 */
object PsiClassAnalyzers {
    
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
package com.addzero.util.lsi_impl.impl.psi

import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil

/**
 * Java类型分析器集合
 */
object PsiTypeAnalyzers {

    /**
     * 集合类型分析器
     */
    object CollectionTypeAnalyzer {
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
     * 可空性分析器
     */
    object NullabilityAnalyzer {
        fun isNullable(psiType: PsiType): Boolean {
            // Void类型总是可空的
            if (psiType == PsiTypes.voidType()) return true

            // 基本类型不可空
            if (psiType is PsiPrimitiveType) return false

            // 检查是否有 Nullable 注解
            for (annotation in psiType.annotations) {
                val shortName = annotation.nameReferenceElement?.referenceName
                if (shortName != null && shortName.contains("Null", ignoreCase = true)) {
                    return true
                }
            }

            // 默认情况下，非基本类型可以为空
            return true
        }
    }
}

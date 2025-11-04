package com.addzero.util.lsi.impl.psi

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiClassType
import com.intellij.psi.util.InheritanceUtil

/**
 * Java字段分析器集合
 */
object PsiFieldAnalyzers {

    /**
     * 静态字段分析器
     */
    object StaticFieldAnalyzer {
        fun isStaticField(psiField: PsiField): Boolean {
            return psiField.hasModifierProperty(PsiModifier.STATIC)
        }
    }

    /**
     * 集合类型分析器
     */
    object CollectionTypeAnalyzer {
        fun isCollectionType(psiField: PsiField): Boolean {
            return isCollectionType(psiField.type)
        }

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
     * 常量字段分析器
     */
    object ConstantFieldAnalyzer {
        fun isConstantField(psiField: PsiField): Boolean {
            return psiField.hasModifierProperty(PsiModifier.FINAL) &&
                    psiField.hasModifierProperty(PsiModifier.STATIC)
        }
    }
}

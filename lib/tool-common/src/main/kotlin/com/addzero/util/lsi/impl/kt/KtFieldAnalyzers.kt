package com.addzero.util.lsi.impl.kt

import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * Kotlin字段分析器集合
 */
object KtFieldAnalyzers {
    
    /**
     * 静态字段分析器
     */
    object StaticFieldAnalyzer {
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
    }

    /**
     * 集合类型分析器
     */
    object CollectionTypeAnalyzer {
        fun isCollectionType(ktProperty: KtProperty): Boolean {
            // 集合类型的全限定名列表
            val COLLECTION_TYPE_FQ_NAMES = setOf(
                "java.util.Collection", "java.util.List", "java.util.Set", "java.util.Map",
                "kotlin.collections.Collection", "kotlin.collections.List", "kotlin.collections.Set",
                "kotlin.collections.Map", "kotlin.collections.ArrayList", "kotlin.collections.LinkedList",
                "kotlin.collections.HashSet", "kotlin.collections.LinkedHashSet",
                "kotlin.collections.HashMap", "kotlin.collections.LinkedHashMap"
            )

            // 获取类的全限定名
            val fqName = ktProperty.fqName?.asString() ?: return false

            // 检查是否为集合类型
            return COLLECTION_TYPE_FQ_NAMES.any { fqName.startsWith(it) }
        }
    }
}
package com.addzero.util.kt

import com.intellij.psi.PsiClass
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtProperty

object KtTypeUtil {

    /**
     * 集合类型的全限定名列表
     */
    private val COLLECTION_TYPE_FQ_NAMES = setOf(
        "java.util.Collection", "java.util.List", "java.util.Set", "java.util.Map", 
        "kotlin.collections.Collection", "kotlin.collections.List", "kotlin.collections.Set", 
        "kotlin.collections.Map", "kotlin.collections.ArrayList", "kotlin.collections.LinkedList", 
        "kotlin.collections.HashSet", "kotlin.collections.LinkedHashSet", 
        "kotlin.collections.HashMap", "kotlin.collections.LinkedHashMap"
    )

    /**
     * 判断 KtClass 是否为集合类型
     */
    private fun isKtCollection(ktClass: KtClass): Boolean {
        // 获取类的全限定名
        val fqName = ktClass.fqName?.asString() ?: return false

        // 检查是否为集合类型
        return COLLECTION_TYPE_FQ_NAMES.any { fqName.startsWith(it) }
    }

    /**
     * 综合检查类是否为集合类型（包括继承关系）
     */
    fun KtProperty.isCollectionType(): Boolean {
        // 获取类的全限定名
        val fqName = this.fqName?.asString() ?: return false

        // 检查是否为集合类型
        return COLLECTION_TYPE_FQ_NAMES.any { fqName.startsWith(it) }
    }
}
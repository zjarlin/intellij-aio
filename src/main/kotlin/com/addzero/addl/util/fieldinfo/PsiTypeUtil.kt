package com.addzero.addl.util.fieldinfo

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtProperty

object PsiTypeUtil {


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
     * 判断 PsiClass 是否为集合类型
     */
    private fun isCollectionPsiClass(psiClass: PsiClass): Boolean {
        // 获取类的全限定名
        val qualifiedName = psiClass.qualifiedName ?: return false

        // 检查是否为集合类型
        return COLLECTION_TYPE_FQ_NAMES.any { qualifiedName.startsWith(it) }
    }

    /**
     * 集合类型的全限定名列表
     */
    private val COLLECTION_TYPE_FQ_NAMES = setOf(
        "java.util.Collection", "java.util.List", "java.util.Set", "java.util.Map", "kotlin.collections.Collection", "kotlin.collections.List", "kotlin.collections.Set", "kotlin.collections.Map", "kotlin.collections.ArrayList", "kotlin.collections.LinkedList", "kotlin.collections.HashSet", "kotlin.collections.LinkedHashSet", "kotlin.collections.HashMap", "kotlin.collections.LinkedHashMap"
    )

    /**
     * 检查类是否继承自集合类型
     */
    private fun isCollectionSupertype(psiClass: PsiClass): Boolean {
        return psiClass.supers.any { superClass ->
            val qualifiedName = superClass.qualifiedName ?: return@any false
            COLLECTION_TYPE_FQ_NAMES.any { qualifiedName.startsWith(it) }
        }
    }

    /**
     * 综合检查类是否为集合类型（包括继承关系）
     */
    fun isCollectionType(psiClass: PsiClass): Boolean {
        return isCollectionPsiClass(psiClass) || isCollectionSupertype(psiClass)
    }



    /**
     * 综合检查类是否为集合类型（包括继承关系）
     */
    fun isCollectionType(psiClass: KtProperty): Boolean {
        // 获取类的全限定名
        val fqName = psiClass.fqName?.asString() ?: return false

        // 检查是否为集合类型
        return COLLECTION_TYPE_FQ_NAMES.any { fqName.startsWith(it) }

    }


    /**
     * 检查类型是否为泛型集合类型
     */
    fun isGenericCollectionType(psiClass: PsiClass): Boolean {
        // 检查是否有类型参数
        val hasTypeParameters = psiClass.typeParameters.isNotEmpty()
        return hasTypeParameters && isCollectionType(psiClass)
    }

    fun isCollectionType(psiField: PsiField):Boolean {
        // 获取类的全限定名
        val type = psiField.type
        val clazz = type.clazz()
        if (clazz==null) {
            return false
        }
        return isCollectionType(clazz)

    }
}
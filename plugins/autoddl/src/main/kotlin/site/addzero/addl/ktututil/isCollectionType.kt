package site.addzero.addl.ktututil

import site.addzero.common.kt_util.isNotNull
import com.intellij.psi.PsiType
import com.intellij.psi.PsiClassType
import com.intellij.psi.CommonClassNames
import com.intellij.psi.util.InheritanceUtil



/**
 * 获取集合的元素类型
 */
fun PsiType.getCollectionElementType(): PsiType? {
    if (!this.isCollectionType()) return null
    return (this as? PsiClassType)?.parameters?.firstOrNull()
}

// 集合类型的全限定名列表
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

// Kotlin 集合类型的简单名称列表
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

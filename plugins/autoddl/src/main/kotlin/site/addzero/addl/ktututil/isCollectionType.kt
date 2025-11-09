package site.addzero.addl.ktututil

import site.addzero.common.kt_util.isNotNull
import com.intellij.psi.PsiType
import com.intellij.psi.PsiClassType
import com.intellij.psi.CommonClassNames
import com.intellij.psi.util.InheritanceUtil

/**
 * 判断 PsiType 是否为集合类型的扩展函数
 */
fun PsiType.isCollectionType(): Boolean {

    // 如果不是类类型，直接返回 false
    if (this !is PsiClassType) return false

    // 获取完全限定名
    val qualifiedName = resolve()?.qualifiedName ?: return false

    val b = when {
        // 检查是否为 Java 集合类型
        InheritanceUtil.isInheritor(this, CommonClassNames.JAVA_UTIL_COLLECTION) -> true
        InheritanceUtil.isInheritor(this, CommonClassNames.JAVA_UTIL_MAP) -> true

        // 检查是否为具体的集合类型
        qualifiedName in COLLECTION_TYPE_FQ_NAMES -> true

        // 检查是否为 Kotlin 集合类型
        qualifiedName.startsWith("kotlin.collections.") -> {
            val simpleName = qualifiedName.substringAfterLast(".")
            simpleName in KOTLIN_COLLECTION_SIMPLE_NAMES
        }

        else -> false
    }
    return b
}

/**
 * 判断是否为可空的集合类型
 */
fun PsiType.isNullableCollectionType(): Boolean {
    return this.isCollectionType() && !this.isNotNull()
}

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

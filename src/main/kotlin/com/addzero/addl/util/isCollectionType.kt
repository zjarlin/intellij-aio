package com.addzero.addl.util

import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName

/**
 * 扩展函数：判断 KtProperty 是否为集合类型
 */
fun KtProperty.isCollectionType(): Boolean {
    // 获取属性的类型引用
    val typeRef = this.typeReference ?: return false
    return typeRef.isCollectionType()
}

/**
 * 扩展函数：判断 KtTypeReference 是否为集合类型
 */
fun KtTypeReference.isCollectionType(): Boolean {
    // 获取类型的文本表示
    val typeText = this.text ?: return false

    // 检查是否为集合类型
    return COLLECTION_TYPE_NAMES.any { collectionType ->
        typeText.startsWith(collectionType) ||
        typeText.contains("<") && typeText.contains(collectionType)
    }
}

/**
 * 获取集合的元素类型
 */
fun KtProperty.getCollectionElementType(): KtTypeReference? {
    if (!this.isCollectionType()) return null

    val typeRef = this.typeReference ?: return null
    // 获取泛型参数
    return typeRef.typeElement
        ?.typeArgumentsAsTypes
        ?.firstOrNull()
}

/**
 * 判断是否为可空集合类型
 */
fun KtProperty.isNullableCollectionType(): Boolean {
    val typeRef = this.typeReference ?: return false
    return typeRef.isCollectionType() && typeRef.text?.endsWith("?") == true
}

/**
 * 获取详细的集合类型信息
 */
fun KtProperty.getCollectionTypeInfo(): CollectionTypeInfo? {
    val typeRef = this.typeReference ?: return null
    if (!typeRef.isCollectionType()) return null

    val typeText = typeRef.text ?: return null
    val isNullable = typeText.endsWith("?")

    return CollectionTypeInfo(
        collectionType = when {
            typeText.contains("List") -> CollectionKind.LIST
            typeText.contains("Set") -> CollectionKind.SET
            typeText.contains("Map") -> CollectionKind.MAP
            typeText.contains("Collection") -> CollectionKind.COLLECTION
            else -> CollectionKind.OTHER
        },
        isNullable = isNullable,
        isMutable = typeText.startsWith("Mutable"),
        elementType = getCollectionElementType()
    )
}

/**
 * 集合类型信息数据类
 */
data class CollectionTypeInfo(
    val collectionType: CollectionKind,
    val isNullable: Boolean,
    val isMutable: Boolean,
    val elementType: KtTypeReference?
)

/**
 * 集合类型枚举
 */
enum class CollectionKind {
    LIST,
    SET,
    MAP,
    COLLECTION,
    OTHER
}

// 集合类型名称列表
private val COLLECTION_TYPE_NAMES = setOf(
    "List",
    "MutableList",
    "ArrayList",
    "LinkedList",
    "Set",
    "MutableSet",
    "HashSet",
    "TreeSet",
    "Map",
    "MutableMap",
    "HashMap",
    "TreeMap",
    "Collection",
    "MutableCollection"
)
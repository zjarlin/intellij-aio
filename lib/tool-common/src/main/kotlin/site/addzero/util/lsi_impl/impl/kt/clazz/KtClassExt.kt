package site.addzero.util.lsi_impl.impl.kt.clazz

import site.addzero.util.lsi.assist.getDefaultValueForType
import site.addzero.util.lsi.assist.getDefaultAnyValueForType
import site.addzero.util.lsi.assist.isCollectionType
import site.addzero.util.lsi.assist.isCustomObjectType
import site.addzero.util.lsi.constant.DATA_ANNOTATIONS_SHORT
import site.addzero.util.lsi.constant.ENTITY_ANNOTATIONS_SHORT
import site.addzero.util.lsi.constant.TABLE_ANNOTATIONS_SHORT
import site.addzero.util.lsi_impl.impl.kt.anno.guessTableName
import site.addzero.util.lsi_impl.impl.kt.anno.simplaName
import site.addzero.util.lsi_impl.impl.psi.project.createListJson
import site.addzero.util.lsi_impl.impl.psi.project.findKtClassByName
import com.google.gson.JsonObject
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.psi.KtClass
import site.addzero.util.str.cleanDocComment
import site.addzero.util.str.firstNotBlank
import site.addzero.util.str.removeAnyQuote
import site.addzero.util.str.toUnderLineCase

fun KtClass.isPojo(): Boolean {
    if (isInterface() || isEnum()) {
        return false
    }
    val annotations = this.annotationEntries.mapNotNull { it.simplaName }
    // 检查是否有实体注解
    val hasEntityAnnotation = annotations.any { it in ENTITY_ANNOTATIONS_SHORT }
    val hasTableAnnotation = annotations.any { it in TABLE_ANNOTATIONS_SHORT }
    val hasDataAnnotation = annotations.any { it in DATA_ANNOTATIONS_SHORT }
    val isDataClass = isData()
    return hasEntityAnnotation || hasTableAnnotation || hasDataAnnotation || isDataClass
}


fun KtClass.qualifiedName(): String? {
    return this.fqName?.asString()
}

fun KtClass.isCollectionType(): Boolean {
    // 获取类的全限定名
    val qualifiedName = qualifiedName()
    val fqName = qualifiedName ?: return false
    val collectionType = qualifiedName.isCollectionType()
    return collectionType
}


fun KtClass.docComment(): String {
    return cleanDocComment(this.docComment?.text)
}


fun KtClass.guessTableEnglishName(): String {
    val text = this.name?.toUnderLineCase()
    val guessTableNameByAnno = this.guessTableNameByAnno()
    val firstNotBlank = firstNotBlank(guessTableNameByAnno, text)
    return firstNotBlank.removeAnyQuote()
}

fun KtClass.guessTableNameByAnno(): String? {
    val toLightClass = toLightClass()
    val annotations = toLightClass?.annotations
    return annotations.guessTableName()
}

/**
 * 将 KtClass 转换为 JsonObject 结构
 * 支持嵌套对象和 List 类型
 *
 * @return JsonObject 表示的类结构
 */
fun KtClass.ktClassToJson(): JsonObject {
    val project = this.project
    val jsonObject = JsonObject()
    // 提取 KtClass 的属性
    getProperties().forEach { property ->
        val propertyType = property.typeReference?.text ?: "Any"
        val propertyName = property.name ?: return@forEach

        // 检查是否是嵌套对象或 List
        if (isCustomObjectType(propertyType)) {
            val nestedClass = project.findKtClassByName(propertyType)
            nestedClass?.let { jsonObject.add(propertyName, it.ktClassToJson()) }
        } else if (propertyType.startsWith("List<")) {
            val elementType = propertyType.removePrefix("List<").removeSuffix(">")
            jsonObject.add(propertyName, project.createListJson(elementType))
        } else {
            jsonObject.addProperty(propertyName, getDefaultValueForType(propertyType))
        }
    }

    return jsonObject
}

// ============ Map 生成相关 ============

private const val MAX_RECURSION_DEPTH = 3

/**
 * 将 KtClass 转换为 Map 结构，支持嵌套对象和集合类型
 * 使用递归深度限制防止无限递归
 *
 * @param depth 当前递归深度，默认为 0
 * @return 表示类结构的 Map，key 为字段名，value 为示例值
 */
fun KtClass.generateMap(depth: Int = 0): Map<String, Any?> {
    if (depth > MAX_RECURSION_DEPTH) return emptyMap()

    val project = this.project
    val outputMap = LinkedHashMap<String, Any?>()

    getProperties().forEach { property ->
        val propertyType = property.typeReference?.text
        val propertyName = property.name
        if (propertyName != null) {
            outputMap[propertyName] = propertyType.getObjectForType(project, this, depth + 1)
        }
    }

    return outputMap
}

/**
 * 根据类型名称生成对应的示例值
 * 支持基本类型、集合类型、数组类型和自定义类型
 */
private fun String?.getObjectForType(
    project: Project,
    containingClass: KtClass,
    depth: Int = 0
): Any? {
    if (depth > MAX_RECURSION_DEPTH) return null

    return when {
        this == null -> null
        startsWith("List<") -> handleListType(this, project, containingClass, depth)
        startsWith("Array<") -> handleArrayType(this, project, containingClass, depth)
        else -> getPrimitiveOrCustomValue(this, project, depth)
    }
}

/**
 * 处理 List 类型
 */
private fun handleListType(
    typeName: String,
    project: Project,
    containingClass: KtClass,
    depth: Int
): List<Any?> {
    if (depth > MAX_RECURSION_DEPTH) return emptyList()

    val elementType = typeName.substringAfter("List<").substringBeforeLast(">")
    val sampleValue = elementType.getObjectForType(project, containingClass, depth + 1)
    return listOf(sampleValue)
}

/**
 * 处理 Array 类型
 */
private fun handleArrayType(
    typeName: String,
    project: Project,
    containingClass: KtClass,
    depth: Int
): List<Any?> {
    if (depth > MAX_RECURSION_DEPTH) return emptyList()

    val elementType = typeName.substringAfter("Array<").substringBeforeLast(">")
    val sampleValue = elementType.getObjectForType(project, containingClass, depth + 1)
    return listOf(sampleValue)
}

/**
 * 获取基本类型或自定义类型的示例值
 * 使用统一的类型默认值函数
 */
private fun getPrimitiveOrCustomValue(typeName: String, project: Project, depth: Int): Any? {
    if (depth > MAX_RECURSION_DEPTH) return null

    // 先尝试使用统一的类型默认值函数
    val defaultValue = getDefaultAnyValueForType(typeName)

    // 如果返回的是类型名本身（表示不是已知的基本类型），则尝试解析为自定义类
    return if (defaultValue == typeName) {
        // 处理自定义类型 - 根据类名查找并递归生成
        val targetClass = project.findKtClassByName(typeName)
        targetClass?.generateMap(depth + 1)
            ?: mapOf("type" to typeName)
    } else {
        defaultValue
    }
}


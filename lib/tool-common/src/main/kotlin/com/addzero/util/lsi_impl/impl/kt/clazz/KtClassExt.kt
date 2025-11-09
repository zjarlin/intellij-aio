package com.addzero.util.lsi_impl.impl.kt.clazz

import com.addzero.util.lsi.assist.getDefaultValueForType
import com.addzero.util.lsi.assist.isCollectionType
import com.addzero.util.lsi.assist.isCustomObjectType
import com.addzero.util.lsi.constant.DATA_ANNOTATIONS_SHORT
import com.addzero.util.lsi.constant.ENTITY_ANNOTATIONS_SHORT
import com.addzero.util.lsi.constant.TABLE_ANNOTATIONS_SHORT
import com.addzero.util.lsi_impl.impl.kt.anno.guessTableName
import com.addzero.util.lsi_impl.impl.kt.anno.simplaName
import com.addzero.util.lsi_impl.impl.psi.project.createListJson
import com.addzero.util.lsi_impl.impl.psi.project.findKtClassByName
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

fun KtClass.ktClassToJson(project: Project): JsonObject {
    val jsonObject = JsonObject()
    // 提取 KtClass 的属性
    getProperties().forEach { property ->
        val propertyType = property.typeReference?.text ?: "Any"
        val propertyName = property.name ?: return@forEach

        // 检查是否是嵌套对象或 List
        if (isCustomObjectType(propertyType)) {
            val nestedClass = project.findKtClassByName(propertyType)
            nestedClass?.let { jsonObject.add(propertyName, it.ktClassToJson(project)) }
        } else if (propertyType.startsWith("List<")) {
            val elementType = propertyType.removePrefix("List<").removeSuffix(">")
            jsonObject.add(propertyName, project.createListJson(elementType))
        } else {
            jsonObject.addProperty(propertyName, getDefaultValueForType(propertyType))
        }
    }

    return jsonObject
}


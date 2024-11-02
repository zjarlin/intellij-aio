package com.addzero.addl.util.kt_util

import com.google.gson.JsonObject
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.psi.KtClass

object Psi2Json {

    /**
     * 将 KtClass 转换为 JSON
     */
    fun ktClassToJson(ktClass: KtClass, project: Project): JsonObject {
        val jsonObject = JsonObject()

        // 提取 KtClass 的属性
        ktClass.getProperties().forEach { property ->
            val propertyType = property.typeReference?.text ?: "Any"
            val propertyName = property.name ?: return@forEach

            // 检查是否是嵌套对象或 List
            if (isCustomObjectType(propertyType)) {
                val nestedClass = findKtClassByName(propertyType, project)
                nestedClass?.let { jsonObject.add(propertyName, ktClassToJson(it, project)) }
            } else if (propertyType.startsWith("List<")) {
                val elementType = propertyType.removePrefix("List<").removeSuffix(">")
                jsonObject.add(propertyName, createListJson(elementType, project))
            } else {
                jsonObject.addProperty(propertyName, getDefaultForType(propertyType))
            }
        }

        return jsonObject
    }

    /**
     * 将 PsiClass 转换为 JSON
     */
    fun psiClassToJson(psiClass: PsiClass, project: Project): JsonObject {
        val jsonObject = JsonObject()

        // 提取 PsiClass 的字段
        psiClass.allFields.forEach { field ->
            val fieldType = field.type.presentableText
            val fieldName = field.name

            // 检查是否是嵌套对象或 List
            if (isCustomObjectType(fieldType)) {
                val nestedClass = PsiTypesUtil.getPsiClass(field.type)
                nestedClass?.let { jsonObject.add(fieldName, psiClassToJson(it, project)) }
            } else if (fieldType.startsWith("List<")) {
                val elementType = fieldType.removePrefix("List<").removeSuffix(">")
                jsonObject.add(fieldName, createListJson(elementType, project))
            } else {
                jsonObject.addProperty(fieldName, getDefaultForType(fieldType))
            }
        }

        return jsonObject
    }

    /**
     * Helper: 创建 List 类型的 JSON 内容
     */
    fun createListJson(elementType: String, project: Project): JsonObject {
        val listJson = JsonObject()
        if (isCustomObjectType(elementType)) {
            val elementClass = findKtClassByName(elementType, project)
            elementClass?.let { listJson.add("element", ktClassToJson(it, project)) }
        } else {
            listJson.addProperty("element", getDefaultForType(elementType))
        }
        return listJson
    }

    /**
     * Helper: 根据类型返回默认值
     */
    fun getDefaultForType(typeName: String): String {
        return when (typeName) {
            "Int" -> "0"
            "Boolean" -> "true"
            "Double" -> "0.0"
            "Float" -> "0.0f"
            "Long" -> "0L"
            "String" -> "\"\""
            else -> "\"\""
        }
    }

    /**
     * Helper: 检查是否是自定义对象类型
     */
    fun isCustomObjectType(typeName: String): Boolean {
        return !(typeName in listOf("Int", "Boolean", "Double", "Float", "Long", "String", "Any"))
    }

    /**
     * 查找 KtClass by 名称
     */

    private fun findKtClassByName(className: String, project: Project): KtClass? {
        // 使用 Helper 查找 Kotlin 类
        val possibleClasses =
            KotlinFullClassNameIndex.Helper.get(className, project, GlobalSearchScope.projectScope(project))

        return possibleClasses.firstOrNull() as? KtClass
    }

}
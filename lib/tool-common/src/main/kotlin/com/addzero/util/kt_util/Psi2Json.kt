package com.addzero.util.kt_util

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtProperty
import java.util.*

object Psi2Json {

    private const val MAX_RECURSION_DEPTH = 3

    fun generateMap(ktClass: KtClass, project: Project, depth: Int = 0): Map<String, Any?> {
        if (depth > MAX_RECURSION_DEPTH) return emptyMap()

        val outputMap = LinkedHashMap<String, Any?>()

        ktClass.getProperties().forEach { property ->
            val propertyType = property.typeReference?.text
            val propertyName = property.name
            if (propertyName != null) {
                outputMap[propertyName] = getObjectForType(propertyType, project, ktClass, depth + 1)
            }
        }

        return outputMap
    }

    private fun getObjectForType(
        typeName: String?,
        project: Project,
        containingClass: KtClass,
        depth: Int = 0
    ): Any? {
        if (depth > MAX_RECURSION_DEPTH) return null

        return when {
            typeName == null -> null
            typeName.startsWith("List<") -> handleListType(typeName, project, containingClass, depth + 1)
            typeName.startsWith("Array<") -> handleArrayType(typeName, project, containingClass, depth + 1)
            else -> when (typeName) {
                "Int", "Integer" -> 0
                "Boolean" -> false
                "Byte" -> 0.toByte()
                "Char", "Character" -> ' '
                "Double" -> 0.0
                "Float" -> 0.0f
                "Long" -> 0L
                "Short" -> 0.toShort()
                "String" -> ""
                "LocalDate" -> "2024-03-22"
                "LocalDateTime" -> "2024-03-22 12:00:00"
                "BigDecimal" -> "0.00"
                else -> {
                    // 处理自定义类型
                    val targetClass =project findKtClassByName(typeName)
                    targetClass?.let { generateMap(it, project, depth + 1) }
                        ?: mapOf("type" to typeName)
                }
            }
        }
    }

    private fun handleListType(
        typeName: String,
        project: Project,
        containingClass: KtClass,
        depth: Int = 0
    ): List<Any?> {
        if (depth > MAX_RECURSION_DEPTH) return emptyList()

        val elementType = typeName.substringAfter("List<").substringBeforeLast(">")
        val sampleValue = getObjectForType(elementType, project, containingClass, depth + 1)

        return listOf(sampleValue)
    }

    private fun handleArrayType(
        typeName: String,
        project: Project,
        containingClass: KtClass,
        depth: Int = 0
    ): List<Any?> {
        if (depth > MAX_RECURSION_DEPTH) return emptyList()

        val elementType = typeName.substringAfter("Array<").substringBeforeLast(">")
        val sampleValue = getObjectForType(elementType, project, containingClass, depth + 1)

        return listOf(sampleValue)
    }

}

package com.addzero.util.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiType
import java.util.*

object Psi2Json {
    private const val MAX_RECURSION_DEPTH = 3

    fun generateMap(psiClass: PsiClass, project: Project, depth: Int = 0): Map<String, Any?> {
        if (depth > MAX_RECURSION_DEPTH) return emptyMap()

        val outputMap = LinkedHashMap<String, Any?>()

        psiClass.allFields.forEach { field ->
            val fieldType = field.type
            val fieldName = field.name
            if (fieldName != null) {
                outputMap[fieldName] = getObjectForType(fieldType, project, psiClass, depth + 1)
            }
        }
        return outputMap
    }

    private fun getObjectForType(
        psiType: PsiType,
        project: Project,
        containingClass: PsiClass,
        depth: Int = 0
    ): Any? {
        if (depth > MAX_RECURSION_DEPTH) return null

        return when {
            psiType is com.intellij.psi.PsiArrayType -> handleArrayType(psiType, project, containingClass, depth + 1)
            psiType is com.intellij.psi.PsiClassType && psiType.isCollectionType() -> handleCollectionType(
                psiType,
                project,
                containingClass,
                depth + 1
            )

            else -> {
                val presentableText = psiType.presentableText
                when (presentableText) {
                    "int", "Integer" -> 0
                    "boolean", "Boolean" -> false
                    "byte", "Byte" -> 0.toByte()
                    "char", "Character" -> ' '
                    "double", "Double" -> 0.0
                    "float", "Float" -> 0.0f
                    "long", "Long" -> 0L
                    "short", "Short" -> 0.toShort()
                    "String" -> ""
                    "LocalDate" -> "2024-03-22"
                    "LocalDateTime" -> "2024-03-22 12:00:00"
                    "BigDecimal" -> "0.00"
                    else -> {
                        // 处理自定义类型
                        val targetClass = psiType.resolve()
                        targetClass?.let { generateMap(it, project, depth + 1) }
                            ?: mapOf("type" to psiType.presentableText)
                    }
                }
            }
        }
    }

    private fun handleArrayType(
        psiType: com.intellij.psi.PsiArrayType,
        project: Project,
        containingClass: PsiClass,
        depth: Int = 0
    ): List<Any?> {
        if (depth > MAX_RECURSION_DEPTH) return emptyList()

        val componentType = psiType.componentType
        val sampleValue = getObjectForType(componentType, project, containingClass, depth + 1)

        return listOf(sampleValue)
    }

    private fun handleCollectionType(
        psiType: com.intellij.psi.PsiClassType,
        project: Project,
        containingClass: PsiClass,
        depth: Int = 0
    ): List<Any?> {
        if (depth > MAX_RECURSION_DEPTH) return emptyList()

        val elementType = psiType.parameters.firstOrNull()
        val sampleValue = elementType?.let { getObjectForType(it, project, containingClass, depth + 1) }

        return listOfNotNull(sampleValue)
    }

    private fun PsiClassType.isCollectionType(): Boolean {
        val qualifiedName = resolve()?.qualifiedName ?: return false
        return qualifiedName.startsWith("java.util.") && (
                qualifiedName.contains("List") ||
                        qualifiedName.contains("Set") ||
                        qualifiedName.contains("Collection")
                )
    }
}

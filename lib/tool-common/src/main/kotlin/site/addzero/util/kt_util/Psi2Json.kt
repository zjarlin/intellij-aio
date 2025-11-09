package site.addzero.util.kt_util

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
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
            psiType is PsiArrayType -> handleArrayType(psiType, project, containingClass, depth + 1)
            psiType is PsiClassType && psiType.isCollectionType() -> handleCollectionType(
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
        psiType: PsiArrayType,
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
        psiType: PsiClassType,
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

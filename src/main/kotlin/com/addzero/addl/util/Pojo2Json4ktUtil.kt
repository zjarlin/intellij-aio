package com.addzero.addl.util

import com.addzero.addl.util.fieldinfo.clazz
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.nj2k.postProcessing.type
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtProperty
import java.util.*

object Pojo2Json4ktUtil {
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

    private fun getObjectForType(typeName: String?, project: Project, containingClass: KtClass, depth: Int = 0): Any? {
        if (depth > MAX_RECURSION_DEPTH) return null

        return when {
            typeName == null -> null
            typeName.startsWith("List<") -> handleListType(typeName, project, containingClass, depth + 1)
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
                    val targetClass = findKtClassByName(typeName, project)
                    targetClass?.let { generateMap(it, project, depth + 1) }
                        ?: mapOf("type" to typeName)
                }
            }
        }
    }

    private fun handleListType(typeName: String, project: Project, containingClass: KtClass, depth: Int = 0): List<Any?> {
        if (depth > MAX_RECURSION_DEPTH) return emptyList()

        val elementType = typeName.substringAfter("List<").substringBeforeLast(">")
        val sampleValue = getObjectForType(elementType, project, containingClass, depth + 1)

        return listOf(sampleValue)
    }

    private fun findKtClassByName(className: String, project: Project): KtClass? {
        // 使用 JavaPsiFacade 查找类
        val psiFacade = JavaPsiFacade.getInstance(project)
        val scope = GlobalSearchScope.projectScope(project)

        // 先尝试直接查找完整类名
        val psiClass = psiFacade.findClass(className, scope)

        // 如果找到了类，并且是 Kotlin Light Class，则获取对应的 KtClass
        if (psiClass is KtLightClass) {
            return psiClass.kotlinOrigin as? KtClass
        }

        // 如果没有找到，尝试在不同的包中查找
        val shortName = className.substringAfterLast('.')
        val foundClasses = psiFacade.findClasses(shortName, scope)

        return foundClasses
            .filterIsInstance<KtLightClass>()
            .firstOrNull { it.kotlinFqName!!.asString() == className }
            ?.kotlinOrigin as? KtClass
    }
}
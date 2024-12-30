package com.addzero.addl.util

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.PsiTypesUtil
import java.util.*

object Pojo2JsonUtil {
    fun generateMap(psiClass: PsiClass, project: Project): LinkedHashMap<Any?, Any?> {
        val outputMap: LinkedHashMap<Any?, Any?> = LinkedHashMap()
        val psiFields = psiClass.fields

        for (field in psiFields) {
            outputMap[field.name] = getObjectForField(field, project)
        }

        return outputMap
    }

    private fun getObjectForField(psiField: PsiField, project: Project): Any {
        val type = psiField.type
        val canonicalText = type.canonicalText

        // 处理原始类型
        if (type is PsiPrimitiveType) {
            return when (canonicalText) {
                "int" -> 0
                "boolean" -> true
                "byte" -> 1.toByte()
                "char" -> '-'
                "double" -> 0.0
                "float" -> 0.0f
                "long" -> 0L
                "short" -> 0.toShort()
                else -> canonicalText
            }
        }

        // 处理包装类型和其他类型
        return when {
            canonicalText == "java.lang.Integer" || canonicalText == "java.lang.Long" -> 0
            canonicalText == "java.lang.Double" || canonicalText == "java.lang.Float" -> 0.0
            canonicalText == "java.lang.Boolean" -> true
            canonicalText == "java.lang.Byte" -> 1.toByte()
            canonicalText == "java.lang.String" -> "str"
            canonicalText == "java.util.Date" -> Date().time
            isListType(type) -> handleList(type, project, psiField.containingClass!!)
            else -> {
                val resolvedClass = PsiTypesUtil.getPsiClass(type)
                resolvedClass?.let { generateMap(it, project) } ?: canonicalText
            }
        }
    }

    private fun isListType(type: PsiType): Boolean {
        val canonicalText = type.canonicalText
        return canonicalText.startsWith("java.util.List") || 
               canonicalText.startsWith("kotlin.collections.List")
    }

    private fun handleList(psiType: PsiType, project: Project, containingClass: PsiClass): Any {
        val list: MutableList<Any?> = ArrayList()
        if (psiType !is PsiClassType) return list

        val parameters = psiType.parameters
        if (parameters.isEmpty()) return list

        val subType = parameters[0]
        val subTypeCanonicalText = subType.canonicalText

        val value = when {
            isListType(subType) -> handleList(subType, project, containingClass)
            subTypeCanonicalText == "java.lang.String" -> "str"
            subTypeCanonicalText == "java.util.Date" -> Date().time
            else -> {
                val resolvedClass = PsiTypesUtil.getPsiClass(subType)
                resolvedClass?.let { generateMap(it, project) } ?: subTypeCanonicalText
            }
        }
        list.add(value)
        return list
    }

    private fun detectCorrectClassByName(className: String, containingClass: PsiClass, project: Project): PsiClass? {
        val classes = PsiShortNamesCache.getInstance(project)
            .getClassesByName(className, GlobalSearchScope.projectScope(project))

        return when {
            classes.isEmpty() -> null
            classes.size == 1 -> classes[0]
            else -> findClassFromImports(classes, containingClass)
        }
    }

    private fun findClassFromImports(classes: Array<PsiClass>, containingClass: PsiClass): PsiClass? {
        val containingFile = containingClass.containingFile as? PsiJavaFile ?: return null
        val importList = containingFile.importList ?: return null
        val importedQualifiedNames = importList.importStatements.mapNotNull { it.qualifiedName }.toSet()

        return classes.firstOrNull { psiClass ->
            val qualifiedName = psiClass.qualifiedName
            qualifiedName != null && importedQualifiedNames.contains(qualifiedName)
        }
    }
}
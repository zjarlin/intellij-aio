package com.addzero.util.lsi_impl.impl.psi.field

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import java.util.*

/**
 * PsiField ؤ<iU
 */

/**
 * ��W��ؤ<(� JSON I:o
 * 9nW�{����ؤ<
 */
fun PsiField.getDefaultValue(project: Project): Any {
    val type = this.type
    val canonicalText = type.canonicalText

    // ��{�
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

    // �{��v�{�
    return when {
        canonicalText == "java.lang.Integer" || canonicalText == "java.lang.Long" -> 0
        canonicalText == "java.lang.Double" || canonicalText == "java.lang.Float" -> 0.0
        canonicalText == "java.lang.Boolean" -> true
        canonicalText == "java.lang.Byte" -> 1.toByte()
        canonicalText == "java.lang.String" -> "str"
        canonicalText == "java.util.Date" -> Date().time
        type.isListType() -> type.handleListDefaultValue(project, this.containingClass!!)
        else -> {
            val resolvedClass = PsiTypesUtil.getPsiClass(type)
            resolvedClass?.let { it.toDefaultValueMap(project) } ?: canonicalText
        }
    }
}

/**
 * $� PsiType /&: List {�
 */
private fun PsiType.isListType(): Boolean {
    val canonicalText = this.canonicalText
    return canonicalText.startsWith("java.util.List") ||
           canonicalText.startsWith("kotlin.collections.List")
}

/**
 *  List {��ؤ<
 */
private fun PsiType.handleListDefaultValue(project: Project, containingClass: PsiClass): Any {
    val list: MutableList<Any?> = ArrayList()
    if (this !is PsiClassType) return list

    val parameters = this.parameters
    if (parameters.isEmpty()) return list

    val subType = parameters[0]
    val subTypeCanonicalText = subType.canonicalText

    val value = when {
        subType.isListType() -> subType.handleListDefaultValue(project, containingClass)
        subTypeCanonicalText == "java.lang.String" -> "str"
        subTypeCanonicalText == "java.util.Date" -> Date().time
        else -> {
            val resolvedClass = PsiTypesUtil.getPsiClass(subType)
            resolvedClass?.let { it.toDefaultValueMap(project) } ?: subTypeCanonicalText
        }
    }
    list.add(value)
    return list
}

/**
 *  PsiClass lb:ؤ< Map(�LW�a
 */
fun PsiClass.toDefaultValueMap(project: Project): LinkedHashMap<Any?, Any?> {
    val outputMap: LinkedHashMap<Any?, Any?> = LinkedHashMap()
    val psiFields = this.fields

    for (field in psiFields) {
        outputMap[field.name] = field.getDefaultValue(project)
    }

    return outputMap
}

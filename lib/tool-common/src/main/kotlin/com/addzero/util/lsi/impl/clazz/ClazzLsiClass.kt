package com.addzero.util.lsi.impl.clazz

import com.addzero.util.lsi.LsiAnnotation
import com.addzero.util.lsi.LsiClass
import com.addzero.util.lsi.LsiField
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * 基于 Java Class 字节码的 LsiClass 实现
 */
class ClazzLsiClass(private val clazz: Class<*>) : LsiClass {
    override val name: String?
        get() = clazz.simpleName

    override val qualifiedName: String?
        get() = clazz.name

    override val comment: String?
        get() = null // 字节码中不包含注释信息

    override val fields: List<LsiField>
        get() = clazz.declaredFields.map { ClazzLsiField(it) }

    override val annotations: List<LsiAnnotation>
        get() = clazz.annotations.map { ClazzLsiAnnotation(it) }

    override val isInterface: Boolean
        get() = clazz.isInterface

    override val isEnum: Boolean
        get() = clazz.isEnum

    override val isCollectionType: Boolean
        get() = ClazzClassAnalyzers.CollectionTypeAnalyzer.isCollectionType(clazz)

    override val superClasses: List<LsiClass>
        get() = clazz.superclass?.let { listOf(ClazzLsiClass(it)) } ?: emptyList()

    override val interfaces: List<LsiClass>
        get() = clazz.interfaces.map { ClazzLsiClass(it) }
}
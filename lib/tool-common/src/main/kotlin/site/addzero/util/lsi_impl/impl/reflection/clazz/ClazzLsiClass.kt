package site.addzero.util.lsi_impl.impl.reflection.clazz

import site.addzero.util.lsi.anno.LsiAnnotation
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.field.LsiField
import site.addzero.util.lsi.method.LsiMethod
import site.addzero.util.lsi_impl.impl.reflection.kt.anno.ClazzLsiAnnotation
import site.addzero.util.lsi_impl.impl.reflection.field.ClazzLsiField
import site.addzero.util.lsi_impl.impl.reflection.java.method.ClazzLsiMethod

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
        get() = clazz.isCollectionType()

    override val isPojo: Boolean
        get() = clazz.isPojo()

    override val superClasses: List<LsiClass>
        get() = clazz.superclass?.let { listOf(ClazzLsiClass(it)) } ?: emptyList()

    override val interfaces: List<LsiClass>
        get() = clazz.interfaces.map { ClazzLsiClass(it) }

    override val guessTableName: String
        get() = clazz.guessTableName()

    override val methods: List<LsiMethod>
        get() = clazz.declaredMethods.map { ClazzLsiMethod(it) }
}

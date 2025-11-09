package com.addzero.util.lsi_impl.impl.reflection.type

import com.addzero.util.lsi.anno.LsiAnnotation
import com.addzero.util.lsi.clazz.LsiClass
import com.addzero.util.lsi.type.LsiType
import com.addzero.util.lsi_impl.impl.reflection.clazz.isCollectionType
import com.addzero.util.lsi_impl.impl.reflection.clazz.isNullable
import com.addzero.util.lsi_impl.impl.reflection.kt.anno.ClazzLsiAnnotation

/**
 * 基于 Java Class 的 LsiType 实现
 */
class ClazzLsiType(private val clazz: Class<*>) : LsiType {
    override val name: String?
        get() = clazz.simpleName

    override val qualifiedName: String?
        get() = clazz.name

    override val presentableText: String?
        get() = clazz.simpleName

    override val annotations: List<LsiAnnotation>
        get() = clazz.annotations.map { ClazzLsiAnnotation(it) }

    override val isCollectionType: Boolean
        get() = clazz.isCollectionType()

    override val isNullable: Boolean
        get() = clazz.isNullable()

    override val typeParameters: List<LsiType>
        get() = emptyList() // 通过反射很难获得完整的泛型参数信息

    override val isPrimitive: Boolean
        get() = clazz.isPrimitive

    override val componentType: LsiType?
        get() {
            val array = clazz.isArray
            val value = if (array) {
                val clazz = clazz.componentType
                ClazzLsiType(clazz)
            } else {
                null
            }
            return value
        }

    override val isArray: Boolean
        get() {
            return clazz.isArray
        }
    override val psiClass: LsiClass?
        get() = TODO("Not yet implemented")
}

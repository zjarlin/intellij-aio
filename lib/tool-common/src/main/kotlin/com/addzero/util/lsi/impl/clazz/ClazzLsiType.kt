package com.addzero.util.lsi.impl.clazz

import com.addzero.util.lsi.LsiAnnotation
import com.addzero.util.lsi.LsiType

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
        get() = ClazzTypeAnalyzers.CollectionTypeAnalyzer.isCollectionType(clazz)

    override val isNullable: Boolean
        get() = ClazzTypeAnalyzers.NullabilityAnalyzer.isNullable(clazz)

    override val typeParameters: List<LsiType>
        get() = emptyList() // 通过反射很难获得完整的泛型参数信息

    override val isPrimitive: Boolean
        get() = clazz.isPrimitive

    override val componentType: LsiType?
        get() = if (ClazzTypeAnalyzers.ArrayAnalyzer.isArray(clazz)) {
            val componentType = ClazzTypeAnalyzers.ArrayAnalyzer.getComponentType(clazz)
            componentType?.let { ClazzLsiType(it) }
        } else {
            null
        }

    override val isArray: Boolean
        get() = ClazzTypeAnalyzers.ArrayAnalyzer.isArray(clazz)
}
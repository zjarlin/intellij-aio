package com.addzero.util.lsi.impl.clazz

import com.addzero.util.lsi.LsiAnnotation
import com.addzero.util.lsi.LsiField
import com.addzero.util.lsi.LsiType
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * 基于 Java Field 反射的 LsiField 实现
 */
class ClazzLsiField(private val field: Field) : LsiField {
    override val name: String?
        get() = field.name

    override val type: LsiType?
        get() = ClazzLsiType(field.type)

    override val typeName: String?
        get() = field.type.simpleName

    override val comment: String?
        get() = null // 字节码中不包含注释信息

    override val annotations: List<LsiAnnotation>
        get() = field.annotations.map { ClazzLsiAnnotation(it) }

    override val isStatic: Boolean
        get() = ClazzFieldAnalyzers.StaticFieldAnalyzer.isStaticField(field)

    override val isConstant: Boolean
        get() = ClazzFieldAnalyzers.ConstantFieldAnalyzer.isConstantField(field)

    override val isCollectionType: Boolean
        get() = ClazzFieldAnalyzers.CollectionTypeAnalyzer.isCollectionType(field)

    override val defaultValue: String?
        get() = null // 字节码中通常不包含字段默认值信息
        
    override fun isCollectionType(): Boolean = isCollectionType
}
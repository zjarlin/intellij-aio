package com.addzero.util.lsi.impl.clazz

import com.addzero.util.lsi.LsiAnnotation
import com.addzero.util.lsi.LsiClass
import com.addzero.util.lsi.LsiField
import com.addzero.util.lsi.LsiType
import java.lang.reflect.Field

/**
 * 基于 Java Field 反射的 LsiField 实现
 */
class ClazzLsiField(private val clazzField: Field) : LsiField {
    override val name: String?
        get() = clazzField.name

    override val type: LsiType?
        get() = ClazzLsiType(clazzField.type)

    override val typeName: String?
        get() = clazzField.type.simpleName

    override val comment: String?
        get() = ClazzFieldAnalyzers.CommentAnalyzer.getComment(clazzField)

    override val annotations: List<LsiAnnotation>
        get() = clazzField.annotations.map { ClazzLsiAnnotation(it) }

    override val isStatic: Boolean
        get() = ClazzFieldAnalyzers.StaticFieldAnalyzer.isStaticField(clazzField)

    override val isConstant: Boolean
        get() = ClazzFieldAnalyzers.ConstantFieldAnalyzer.isConstantField(clazzField)

    override val isVar: Boolean
        get() = !java.lang.reflect.Modifier.isFinal(clazzField.modifiers)

    override val isLateInit: Boolean
        get() = false  // Java 反射无法检测 Kotlin 的 lateinit

    override val isCollectionType: Boolean
        get() = ClazzFieldAnalyzers.CollectionTypeAnalyzer.isCollectionType(clazzField)

    override val defaultValue: String?
        get() = null // 字节码中通常不包含字段默认值信息

    override fun isCollectionType(): Boolean = isCollectionType

    override val columnName: String?
        get() = ClazzFieldAnalyzers.ColumnNameAnalyzer.getColumnName(clazzField)

    // 新增属性的实现

    override val declaringClass: LsiClass?
        get() = clazzField.declaringClass?.let { clazz -> ClazzLsiClass(clazz) }
    
    override val fieldTypeClass: LsiClass?
        get() = clazzField.type?.let { clazz -> ClazzLsiClass(clazz) }
    
    override val isNestedObject: Boolean
        get() = !clazzField.type.isPrimitive && clazzField.type != String::class.java
    
    override val children: List<LsiField>
        get() = if (isNestedObject) {
            clazzField.type.declaredFields.map { ClazzLsiField(it) }
        } else {
            emptyList()
        }
}
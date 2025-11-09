package site.addzero.util.lsi_impl.impl.reflection.field

import site.addzero.util.lsi.anno.LsiAnnotation
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.field.LsiField
import site.addzero.util.lsi.type.LsiType
import site.addzero.util.lsi_impl.impl.reflection.kt.anno.ClazzLsiAnnotation
import site.addzero.util.lsi_impl.impl.reflection.clazz.ClazzLsiClass
import site.addzero.util.lsi_impl.impl.reflection.type.ClazzLsiType
import java.lang.reflect.Field
import java.lang.reflect.Modifier

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
        get() = clazzField.comment()

    override val annotations: List<LsiAnnotation>
        get() = clazzField.annotations.map { ClazzLsiAnnotation(it) }

    override val isStatic: Boolean
        get() = clazzField.isStaticField()

    override val isConstant: Boolean
        get() = clazzField.isConstantField()

    override val isVar: Boolean
        get() = !Modifier.isFinal(clazzField.modifiers)

    override val isLateInit: Boolean
        get() = false  // Java 反射无法检测 Kotlin 的 lateinit

    override val isCollectionType: Boolean
        get() = clazzField.isCollectionType()

    override val defaultValue: String?
        get() = null // 字节码中通常不包含字段默认值信息

    override fun isCollectionType(): Boolean = isCollectionType

    override val columnName: String?
        get() = clazzField.guessColumnName()

    // 新增属性的实现

    override val declaringClass: LsiClass?
        get() = clazzField.declaringClass?.let { clazz -> ClazzLsiClass(clazz) }

    override val fieldTypeClass: LsiClass?
        get() = ClazzLsiClass(clazzField.type)

    override val isNestedObject: Boolean
        get() = !clazzField.type.isPrimitive && clazzField.type != String::class.java

    override val children: List<LsiField>
        get() = if (isNestedObject) {
            clazzField.type.declaredFields.map { ClazzLsiField(it) }
        } else {
            emptyList()
        }
}

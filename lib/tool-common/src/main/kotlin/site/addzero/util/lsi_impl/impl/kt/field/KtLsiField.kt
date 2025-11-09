package site.addzero.util.lsi_impl.impl.kt.field

import site.addzero.util.lsi.anno.LsiAnnotation
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.field.LsiField
import site.addzero.util.lsi.type.LsiType
import site.addzero.util.lsi_impl.impl.kt.type.KtLsiType
import site.addzero.util.lsi_impl.impl.kt.anno.KtLsiAnnotation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtProperty

/**
 * 基于 Kotlin PSI 的 LsiField 实现
 */
class KtLsiField(private val ktProperty: KtProperty) : LsiField {
    override val name: String?
        get() = ktProperty.name

    override val type: LsiType?
        get() = ktProperty.typeReference?.let { KtLsiType(it) }

    override val typeName: String?
        get() = ktProperty.typeReference?.text

    override val comment: String?
        get() = ktProperty.getComment()

    override val annotations: List<LsiAnnotation>
        get() = ktProperty.annotationEntries.map { KtLsiAnnotation(it) }

    override val isStatic: Boolean
        get() = ktProperty.isStaticField()

    override val isConstant: Boolean
        get() = ktProperty.hasModifier(KtTokens.CONST_KEYWORD)

    override val isVar: Boolean
        get() = ktProperty.isVar

    override val isLateInit: Boolean
        get() = ktProperty.hasModifier(KtTokens.LATEINIT_KEYWORD)

    override val isCollectionType: Boolean
        get() = ktProperty.isCollectionType()

    override val defaultValue: String?
        get() = ktProperty.initializer?.text

    override fun isCollectionType(): Boolean = isCollectionType

    override val columnName: String?
        get() = ktProperty.getColumnName()

    // 新增属性的实现

    override val declaringClass: LsiClass?
        get() = TODO("需要通过KtProperty获取声明该字段的类")

    override val fieldTypeClass: LsiClass?
        get() = TODO("需要解析字段类型并转换为LsiClass")

    override val isNestedObject: Boolean
        get() = TODO("需要根据字段类型判断是否为嵌套对象")

    override val children: List<LsiField>
        get() = TODO("需要获取嵌套对象的字段信息")

    /**
     * 判断 KtProperty 是否为静态字段
     */




}

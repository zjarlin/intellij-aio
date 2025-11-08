package com.addzero.util.lsi.impl.kt

import com.addzero.util.lsi.LsiAnnotation
import com.addzero.util.lsi.LsiClass
import com.addzero.util.lsi.LsiField
import com.addzero.util.lsi.LsiType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

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
        get() = KtFieldAnalyzers.CommentAnalyzer.getComment(ktProperty)

    override val annotations: List<LsiAnnotation>
        get() = ktProperty.annotationEntries.map { KtLsiAnnotation(it) }

    override val isStatic: Boolean
        get() = isStaticField(ktProperty)

    override val isConstant: Boolean
        get() = ktProperty.hasModifier(KtTokens.CONST_KEYWORD)

    override val isVar: Boolean
        get() = ktProperty.isVar

    override val isLateInit: Boolean
        get() = ktProperty.hasModifier(KtTokens.LATEINIT_KEYWORD)

    override val isCollectionType: Boolean
        get() = KtFieldAnalyzers.CollectionTypeAnalyzer.isCollectionType(ktProperty)

    override val defaultValue: String?
        get() = ktProperty.initializer?.text

    override fun isCollectionType(): Boolean = isCollectionType

    override val columnName: String?
        get() = KtFieldAnalyzers.ColumnNameAnalyzer.getColumnName(ktProperty)

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
    private fun isStaticField(ktProperty: KtProperty): Boolean {
        // 检查是否有 const 修饰符
        if (ktProperty.hasModifier(KtTokens.CONST_KEYWORD)) {
            return true
        }

        // 检查是否是伴生对象中的属性
        val isInCompanionObject = ktProperty.getParentOfType<KtObjectDeclaration>(true)?.isCompanion() == true

        // 检查是否是对象声明中的属性
        val isInObject = ktProperty.getParentOfType<KtObjectDeclaration>(true) != null

        // 检查是否有 @JvmStatic 注解
        val hasJvmStatic = ktProperty.annotationEntries.any {
            it.shortName?.asString() == "JvmStatic"
        }

        return when {
            // const 属性一定是静态的
            ktProperty.hasModifier(KtTokens.CONST_KEYWORD) -> true

            // 伴生对象中的 @JvmStatic 属性是静态的
            isInCompanionObject && hasJvmStatic -> true

            // 对象声明中的 @JvmStatic 属性是静态的
            isInObject && hasJvmStatic -> true

            // 顶层属性是静态的
            ktProperty.isTopLevel -> true

            else -> false
        }
    }
}
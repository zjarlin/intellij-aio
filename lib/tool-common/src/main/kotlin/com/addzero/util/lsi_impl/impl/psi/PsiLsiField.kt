package com.addzero.util.lsi_impl.impl.psi

import com.addzero.util.lsi.anno.LsiAnnotation
import com.addzero.util.lsi.clazz.LsiClass
import com.addzero.util.lsi.field.LsiField
import com.addzero.util.lsi.type.LsiType
import com.intellij.psi.PsiField

/**
 * 基于 PSI 的 LsiField 实现
 */
class PsiLsiField(private val psiField: PsiField) : LsiField {
    override val name: String?
        get() = psiField.name

    override val type: LsiType?
        get() = PsiLsiType(psiField.type)

    override val typeName: String?
        get() = psiField.type.presentableText

    override val comment: String?
        get() = PsiFieldAnalyzers.CommentAnalyzer.getComment(psiField)

    override val annotations: List<LsiAnnotation>
        get() = psiField.annotations.map { PsiLsiAnnotation(it) }

    override val isStatic: Boolean
        get() = PsiFieldAnalyzers.StaticFieldAnalyzer.isStaticField(psiField)

    override val isConstant: Boolean
        get() = PsiFieldAnalyzers.ConstantFieldAnalyzer.isConstantField(psiField)

    override val isVar: Boolean
        get() = !psiField.hasModifierProperty(com.intellij.psi.PsiModifier.FINAL)

    override val isLateInit: Boolean
        get() = false  // Java 不支持 lateinit

    override val isCollectionType: Boolean
        get() = PsiFieldAnalyzers.CollectionTypeAnalyzer.isCollectionType(psiField)

    override val defaultValue: String?
        get() = psiField.initializer?.text

    override fun isCollectionType(): Boolean = isCollectionType

    override val columnName: String?
        get() = PsiFieldAnalyzers.ColumnNameAnalyzer.getColumnName(psiField)

    // 新增属性的实现

    override val declaringClass: LsiClass?
        get() = psiField.containingClass?.let { PsiLsiClass(it) }

    override val fieldTypeClass: LsiClass?
        get() = when (val psiType = psiField.type) {
            is com.intellij.psi.PsiClassType -> psiType.resolve()?.let { PsiLsiClass(it) }
            else -> null
        }

    override val isNestedObject: Boolean
        get() = when (val psiType = psiField.type) {
            is com.intellij.psi.PsiClassType -> {
                val psiClass = psiType.resolve()
                psiClass != null && !psiClass.isEnum && !psiClass.isInterface && !psiClass.qualifiedName.isNullOrEmpty()
            }
            else -> false
        }

    override val children: List<LsiField>
        get() = when (val psiType = psiField.type) {
            is com.intellij.psi.PsiClassType -> {
                val psiClass = psiType.resolve()
                if (psiClass != null && !psiClass.isEnum && !psiClass.isInterface) {
                    psiClass.allFields.map { PsiLsiField(it) }
                } else {
                    emptyList()
                }
            }
            else -> emptyList()
        }
}

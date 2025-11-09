package com.addzero.util.lsi_impl.impl.psi.field

import com.addzero.util.lsi.anno.LsiAnnotation
import com.addzero.util.lsi.clazz.LsiClass
import com.addzero.util.lsi.field.LsiField
import com.addzero.util.lsi.type.LsiType
import com.addzero.util.lsi_impl.impl.psi.anno.PsiLsiAnnotation
import com.addzero.util.lsi_impl.impl.psi.clazz.PsiLsiClass
import com.addzero.util.lsi_impl.impl.psi.type.PsiLsiType
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiField
import com.intellij.psi.PsiModifier

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
        get() {
            val comment2 = psiField.getComment()
            return comment2
        }

    override val annotations: List<LsiAnnotation>
        get() = psiField.annotations.map { PsiLsiAnnotation(it) }

    override val isStatic: Boolean
        get() = psiField.isStaticField()

    override val isConstant: Boolean
        get() {
            val constantField1 = psiField.isConstantField()
            return constantField1
        }

    override val isVar: Boolean
        get() = !psiField.hasModifierProperty(PsiModifier.FINAL)

    override val isLateInit: Boolean
        get() = false

    override val isCollectionType: Boolean
        get() {
            val collectionType = psiField.isCollectionType()
            return collectionType
        }

    override val defaultValue: String?
        get() = psiField.initializer?.text

    override fun isCollectionType(): Boolean = isCollectionType

    override val columnName: String?
        get() {
            val columnName = psiField.getColumnName()
            return columnName
        }

    // 新增属性的实现

    override val declaringClass: LsiClass?
        get() = psiField.containingClass?.let { PsiLsiClass(it) }

    override val fieldTypeClass: LsiClass?
        get() = when (val psiType = psiField.type) {
            is PsiClassType -> psiType.resolve()?.let { PsiLsiClass(it) }
            else -> null
        }

    override val isNestedObject: Boolean
        get() = when (val psiType = psiField.type) {
            is PsiClassType -> {
                val psiClass = psiType.resolve()
                psiClass != null && !psiClass.isEnum && !psiClass.isInterface && !psiClass.qualifiedName.isNullOrEmpty()
            }

            else -> false
        }

    override val children: List<LsiField>
        get() = when (val psiType = psiField.type) {
            is PsiClassType -> {
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

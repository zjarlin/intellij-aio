package com.addzero.util.lsi.impl.psi

import com.addzero.util.lsi.LsiAnnotation
import com.addzero.util.lsi.LsiField
import com.addzero.util.lsi.LsiType
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
        get() = psiField.docComment?.text

    override val annotations: List<LsiAnnotation>
        get() = psiField.annotations.map { PsiLsiAnnotation(it) }

    override val isStatic: Boolean
        get() = PsiFieldAnalyzers.StaticFieldAnalyzer.isStaticField(psiField)

    override val isConstant: Boolean
        get() = PsiFieldAnalyzers.ConstantFieldAnalyzer.isConstantField(psiField)

    override val isCollectionType: Boolean
        get() = PsiFieldAnalyzers.CollectionTypeAnalyzer.isCollectionType(psiField)

    override val defaultValue: String?
        get() = psiField.initializer?.text
        
    override fun isCollectionType(): Boolean = isCollectionType
}
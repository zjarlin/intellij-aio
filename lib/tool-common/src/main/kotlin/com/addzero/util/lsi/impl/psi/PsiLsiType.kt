package com.addzero.util.lsi.impl.psi

import com.addzero.util.lsi.LsiAnnotation
import com.addzero.util.lsi.LsiType
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType

/**
 * 基于 PSI 的 LsiType 实现
 */
class PsiLsiType(private val psiType: PsiType) : LsiType {
    override val name: String?
        get() = psiType.presentableText

    override val qualifiedName: String?
        get() = when (psiType) {
            is PsiClassType -> psiType.resolve()?.qualifiedName
            else -> null
        }

    override val presentableText: String?
        get() = psiType.presentableText

    override val annotations: List<LsiAnnotation>
        get() = psiType.annotations.map { PsiLsiAnnotation(it) }

    override val isCollectionType: Boolean
        get() = PsiTypeAnalyzers.CollectionTypeAnalyzer.isCollectionType(psiType)

    override val isNullable: Boolean
        get() = PsiTypeAnalyzers.NullabilityAnalyzer.isNullable(psiType)

    override val typeParameters: List<LsiType>
        get() = when (psiType) {
            is PsiClassType -> psiType.parameters.map { PsiLsiType(it) }
            else -> emptyList()
        }

    override val isPrimitive: Boolean
        get() = psiType is PsiPrimitiveType

    override val componentType: LsiType?
        get() = when (psiType) {
            is PsiArrayType -> PsiLsiType(psiType.componentType)
            else -> null
        }

    override val isArray: Boolean
        get() = psiType is PsiArrayType
}
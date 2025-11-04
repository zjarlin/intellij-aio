package com.addzero.util.lsi.impl.kt

import com.addzero.util.lsi.LsiAnnotation
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
        get() = ktProperty.docComment?.text

    override val annotations: List<LsiAnnotation>
        get() = ktProperty.annotationEntries.map { KtLsiAnnotation(it) }

    override val isStatic: Boolean
        get() = KtFieldAnalyzers.StaticFieldAnalyzer.isStaticField(ktProperty)

    override val isConstant: Boolean
        get() = ktProperty.hasModifier(KtTokens.CONST_KEYWORD)

    override val isCollectionType: Boolean
        get() = KtFieldAnalyzers.CollectionTypeAnalyzer.isCollectionType(ktProperty)

    override val defaultValue: String?
        get() = ktProperty.initializer?.text
        
    override fun isCollectionType(): Boolean = isCollectionType
}
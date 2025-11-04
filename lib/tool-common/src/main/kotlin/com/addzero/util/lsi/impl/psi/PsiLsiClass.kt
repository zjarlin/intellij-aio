package com.addzero.util.lsi.impl.psi

import com.addzero.util.lsi.LsiAnnotation
import com.addzero.util.lsi.LsiClass
import com.addzero.util.lsi.LsiField
import com.intellij.psi.PsiClass

/**
 * 基于 PSI 的 LsiClass 实现
 */
class PsiLsiClass(private val psiClass: PsiClass) : LsiClass {
    override val name: String?
        get() = psiClass.name

    override val qualifiedName: String?
        get() = psiClass.qualifiedName

    override val comment: String?
        get() = psiClass.docComment?.text

    override val fields: List<LsiField>
        get() = psiClass.allFields.map { PsiLsiField(it) }

    override val annotations: List<LsiAnnotation>
        get() = psiClass.annotations.map { PsiLsiAnnotation(it) }

    override val isInterface: Boolean
        get() = psiClass.isInterface

    override val isEnum: Boolean
        get() = psiClass.isEnum

    override val isCollectionType: Boolean
        get() = PsiClassAnalyzers.CollectionTypeAnalyzer.isCollectionType(psiClass)

    override val superClasses: List<LsiClass>
        get() = psiClass.supers.map { PsiLsiClass(it) }

    override val interfaces: List<LsiClass>
        get() = psiClass.interfaces.map { PsiLsiClass(it) }
}
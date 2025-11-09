package com.addzero.util.lsi_impl.impl.psi.type

import com.addzero.util.lsi.anno.LsiAnnotation
import com.addzero.util.lsi.clazz.LsiClass
import com.addzero.util.lsi.constant.JavaNullableType
import com.addzero.util.lsi.type.LsiType
import com.addzero.util.lsi_impl.impl.psi.PsiTypeAnalyzers.CollectionTypeAnalyzer.isCollectionType
import com.addzero.util.lsi_impl.impl.psi.anno.PsiLsiAnnotation
import com.addzero.util.lsi_impl.impl.psi.clazz.PsiLsiClass
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiUtil
import com.intellij.util.ReflectionUtil

/**
 * 基于 PSI 的 LsiType 实现
 */
class PsiLsiType(private val psiType: PsiType) : LsiType {

    fun PsiType.getJavaClassFromPsiType(): Class<*> {
        val clazz = this.clazz()
        val name = clazz?.name
        if (name.isNullOrBlank()) {
            return String::class.java
        }
        val javaType2RefType = javaType2RefType(name)
        if (javaType2RefType.isNullOrBlank()) {
            return String::class.java
        }
        return ReflectionUtil.getClassOrNull(javaType2RefType) ?: String::class.java
    }


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
        get() = psiType.isCollectionType()

    override val isNullable: Boolean
        get() = psiType.presentableText in JavaNullableType.entries.map { it.name }

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

    override val psiClass: LsiClass?
        get() {
            val generic = PsiUtil.resolveGenericsClassInType(this.psiType)
            return if (generic.substitutor == PsiSubstitutor.EMPTY) {
                generic.element?.let { PsiLsiClass(it) }
            } else {
                val propTypeParameters = generic.element?.typeParameters ?: return null
                generic.substitutor.substitute(propTypeParameters[0])?.let { it ->
                    PsiUtil.resolveClassInType(it)?.let { PsiLsiClass(it) }
                }
            }
        }
}

package com.addzero.util.lsi.impl.psi

import com.addzero.util.lsi.LsiAnnotation
import com.addzero.util.lsi.LsiClass
import com.addzero.util.lsi.LsiType
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtil

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
        return com.intellij.util.ReflectionUtil.getClassOrNull(javaType2RefType) ?: String::class.java
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
        get() = PsiTypeAnalyzers.CollectionTypeAnalyzer.isCollectionType(psiType)

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

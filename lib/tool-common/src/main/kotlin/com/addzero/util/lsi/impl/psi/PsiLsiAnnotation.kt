package com.addzero.util.lsi.impl.psi

import com.addzero.util.lsi.LsiAnnotation
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiNameValuePair

/**
 * 基于 PSI 的 LsiAnnotation 实现
 */
class PsiLsiAnnotation(private val psiAnnotation: PsiAnnotation) : LsiAnnotation {
    override val qualifiedName: String?
        get() = psiAnnotation.qualifiedName

    override val simpleName: String?
        get() = psiAnnotation.nameReferenceElement?.referenceName

    override val attributes: Map<String, Any?>
        get() = psiAnnotation.parameterList?.attributes?.associate { attribute ->
            val name = attribute.name ?: "value"
            val value = attribute.value?.text
            name to value
        } ?: emptyMap()

    override fun getAttribute(name: String): Any? {
        return psiAnnotation.findAttributeValue(name)?.text
    }

    override fun hasAttribute(name: String): Boolean {
        return psiAnnotation.findAttributeValue(name) != null
    }
}
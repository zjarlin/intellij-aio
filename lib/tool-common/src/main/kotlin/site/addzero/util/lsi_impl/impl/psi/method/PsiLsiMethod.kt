package site.addzero.util.lsi_impl.impl.psi.method

import site.addzero.util.lsi.anno.LsiAnnotation
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.method.LsiMethod
import site.addzero.util.lsi.method.LsiParameter
import site.addzero.util.lsi.type.LsiType
import site.addzero.util.lsi_impl.impl.psi.anno.PsiLsiAnnotation
import site.addzero.util.lsi_impl.impl.psi.clazz.PsiLsiClass
import site.addzero.util.lsi_impl.impl.psi.type.PsiLsiType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiParameter

/**
 * 基于 PSI 的 LsiMethod 实现
 */
class PsiLsiMethod(private val psiMethod: PsiMethod) : LsiMethod {
    override val name: String?
        get() = psiMethod.name

    override val returnType: LsiType?
        get() = psiMethod.returnType?.let { PsiLsiType(it) }

    override val returnTypeName: String?
        get() = psiMethod.returnType?.presentableText

    override val comment: String?
        get() = psiMethod.getComment()

    override val annotations: List<LsiAnnotation>
        get() = psiMethod.annotations.map { PsiLsiAnnotation(it) }

    override val isStatic: Boolean
        get() = psiMethod.hasModifierProperty(PsiModifier.STATIC)

    override val isAbstract: Boolean
        get() = psiMethod.hasModifierProperty(PsiModifier.ABSTRACT)

    override val parameters: List<LsiParameter>
        get() = psiMethod.parameterList.parameters.map { PsiLsiParameter(it) }

    override val declaringClass: LsiClass?
        get() = psiMethod.containingClass?.let { PsiLsiClass(it) }
}

/**
 * 基于 PSI 的 LsiParameter 实现
 */
class PsiLsiParameter(private val psiParameter: PsiParameter) : LsiParameter {
    override val name: String?
        get() = psiParameter.name

    override val type: LsiType?
        get() = PsiLsiType(psiParameter.type)

    override val typeName: String?
        get() = psiParameter.type.presentableText

    override val annotations: List<LsiAnnotation>
        get() = psiParameter.annotations.map { PsiLsiAnnotation(it) }
}


package site.addzero.util.lsi_impl.impl.kt.`fun`

import site.addzero.util.lsi.*
import site.addzero.util.lsi.anno.LsiAnnotation
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.method.LsiMethod
import site.addzero.util.lsi.method.LsiParameter
import site.addzero.util.lsi.type.LsiType
import site.addzero.util.lsi_impl.impl.kt.anno.KtLsiAnnotation
import site.addzero.util.lsi_impl.impl.kt.clazz.KtLsiClass
import site.addzero.util.lsi_impl.impl.kt.type.KtLsiType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter

/**
 * 基于 Kotlin PSI 的 LsiMethod 实现
 */
class KtLsiMethod(private val ktFunction: KtFunction) : LsiMethod {
    override val name: String?
        get() = ktFunction.name

    override val returnType: LsiType?
        get() = ktFunction.typeReference?.let { KtLsiType(it) }

    override val returnTypeName: String?
        get() = ktFunction.typeReference?.text

    override val comment: String?
        get() = ktFunction.getComment()

    override val annotations: List<LsiAnnotation>
        get() = ktFunction.annotationEntries.map { KtLsiAnnotation(it) }

    override val isStatic: Boolean
        get() = ktFunction.isTopLevel || ktFunction.hasModifier(KtTokens.COMPANION_KEYWORD)

    override val isAbstract: Boolean
        get() = ktFunction.hasModifier(KtTokens.ABSTRACT_KEYWORD)

    override val parameters: List<LsiParameter>
        get() = ktFunction.valueParameters.map { KtLsiParameter(it) }

    override val declaringClass: LsiClass?
        get() {
            val parent = ktFunction.parent
            return if (parent is KtClass) {
                KtLsiClass(parent)
            } else {
                null
            }
        }
}

/**
 * 基于 Kotlin PSI 的 LsiParameter 实现
 */
class KtLsiParameter(private val ktParameter: KtParameter) : LsiParameter {
    override val name: String?
        get() = ktParameter.name

    override val type: LsiType?
        get() = ktParameter.typeReference?.let { KtLsiType(it) }

    override val typeName: String?
        get() = ktParameter.typeReference?.text

    override val annotations: List<LsiAnnotation>
        get() = ktParameter.annotationEntries.map { KtLsiAnnotation(it) }
}


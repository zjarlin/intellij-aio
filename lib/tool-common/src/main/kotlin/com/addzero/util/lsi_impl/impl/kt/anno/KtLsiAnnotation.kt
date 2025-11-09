package com.addzero.util.lsi_impl.impl.kt.anno

import com.addzero.util.lsi.anno.LsiAnnotation
import org.jetbrains.kotlin.psi.KtAnnotationEntry

/**
 * 基于 Kotlin PSI 的 LsiAnnotation 实现
 */
class KtLsiAnnotation(private val ktAnnotation: KtAnnotationEntry) : LsiAnnotation {
    override val qualifiedName: String?
        get() = ktAnnotation.shortName?.asString()

    override val simpleName: String?
        get() = ktAnnotation.shortName?.asString()

    override val attributes: Map<String, Any?>
        get() = ktAnnotation.valueArguments.associate { argument ->
            (argument.getArgumentName()?.asName?.asString() ?: "value") to argument.getArgumentExpression()?.text
        }

    override fun getAttribute(name: String): Any? {
        return ktAnnotation.valueArguments.find {
            it.getArgumentName()?.asName?.asString() == name
        }?.getArgumentExpression()?.text
    }

    override fun hasAttribute(name: String): Boolean {
        return ktAnnotation.valueArguments.any {
            it.getArgumentName()?.asName?.asString() == name
        }
    }
}

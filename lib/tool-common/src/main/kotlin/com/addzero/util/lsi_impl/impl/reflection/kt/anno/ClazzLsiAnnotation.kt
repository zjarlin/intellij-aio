package com.addzero.util.lsi_impl.impl.reflection.kt.anno

import com.addzero.util.lsi.anno.LsiAnnotation

/**
 * 基于 Java Annotation 反射的 LsiAnnotation 实现
 */


class ClazzLsiAnnotation(private val annotation: Annotation) : LsiAnnotation {
    override val simpleName: String?
        get() = annotation.annotationClass.simpleName

    override val qualifiedName: String?
        get() = annotation.annotationClass.qualifiedName

    override val attributes: Map<String, Any?>
        get() = annotation.attributes()

    override fun getAttribute(name: String): Any? {
        return attributes[name]
    }

    override fun hasAttribute(name: String): Boolean {
        return attributes.containsKey(name)
    }
}

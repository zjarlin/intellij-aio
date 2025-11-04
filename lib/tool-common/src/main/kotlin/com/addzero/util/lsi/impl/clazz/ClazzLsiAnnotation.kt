package com.addzero.util.lsi.impl.clazz

import com.addzero.util.lsi.LsiAnnotation

/**
 * 基于 Java Annotation 反射的 LsiAnnotation 实现
 */
class ClazzLsiAnnotation(private val annotation: Annotation) : LsiAnnotation {
    override val name: String?
        get() = annotation.annotationType().simpleName

    override val qualifiedName: String?
        get() = annotation.annotationType().name

    override val attributes: Map<String, Any?>
        get() = annotation.annotationType().declaredMethods.associate { method ->
            method.name to try {
                method.invoke(annotation)
            } catch (e: Exception) {
                null
            }
        }
}
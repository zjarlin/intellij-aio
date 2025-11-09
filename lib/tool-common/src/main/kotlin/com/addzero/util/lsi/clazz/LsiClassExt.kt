package com.addzero.util.lsi.clazz

import com.addzero.util.lsi.field.LsiField


fun LsiClass.filterPropertiesByAnnotations(vararg annotationFqNames: String): List<LsiField> {
    val filter = this.fields.filter { it.hasAnnotation(*annotationFqNames) }
    return filter
}


/**
 * 检查类是否具有指定的注解
 * @param annotationNames 注解全限定名数组
 * @return 如果类具有其中任何一个注解，则返回true，否则返回false
 */
fun LsiClass.hasAnnotation(vararg annotationNames: String): Boolean {
    return annotationNames.any { annotationName ->
        annotations.any { annotation ->
            annotation.qualifiedName == annotationName
        }
    }
}


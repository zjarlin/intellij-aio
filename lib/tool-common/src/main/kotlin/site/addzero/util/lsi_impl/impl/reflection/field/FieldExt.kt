package site.addzero.util.lsi_impl.impl.reflection.field

import site.addzero.util.lsi.constant.COLLECTION_TYPES
import site.addzero.util.lsi.constant.COLUMN_NAME_ANNOTATION_METHOD_MAP
import site.addzero.util.lsi.constant.COMMENT_ANNOTATION_METHOD_MAP
import site.addzero.util.lsi_impl.impl.reflection.kt.anno.getArg
import java.lang.reflect.Field
import java.lang.reflect.Modifier

fun Field.isStaticField(): Boolean {
    return Modifier.isStatic(this.modifiers)
}

fun Field.isCollectionType(): Boolean {
    return COLLECTION_TYPES.any { it.isAssignableFrom(this.type) }
}

fun Field.isConstantField(): Boolean {
    return Modifier.isFinal(this.modifiers) && Modifier.isStatic(this.modifiers)
}


fun Field.comment(): String? {
    // 尝试从注解中获取描述
    this.annotations.forEach { annotation ->
        val annotationName = annotation.annotationClass.java.name
        val methodName = COMMENT_ANNOTATION_METHOD_MAP[annotationName]

        if (methodName != null) {
            val description = annotation.getArg(methodName)
            if (!description.isNullOrBlank()) {
                return description
            }
        }
    }
    return null
}

fun Field.guessColumnName(): String? {
    // 尝试获取注解
    this.annotations.forEach { annotation ->
        val annotationName = annotation.annotationClass.java.name
        val methodName = COLUMN_NAME_ANNOTATION_METHOD_MAP[annotationName] ?: return null
        val value = annotation.getArg(methodName) ?: return null
        return value
    }
    return null
}

package com.addzero.util.lsi_impl.impl.clazz.field

import com.addzero.util.lsi.constant.COLUMN_NAME_ANNOTATION_METHOD_MAP
import com.addzero.util.lsi.constant.COMMENT_ANNOTATION_METHOD_MAP
import com.addzero.util.lsi.constant.COM_BAOMIDOU_MYBATISPLUS_ANNOTATION_TABLE_FIELD
import com.addzero.util.lsi_impl.impl.clazz.anno.getArg
import java.lang.reflect.Field

fun Field.isConstantField(): Boolean {
    return java.lang.reflect.Modifier.isFinal(this.modifiers) && java.lang.reflect.Modifier.isStatic(this.modifiers)
}

fun Annotation.isTargetAnnotation(targetFqName: String): Boolean {
    val fqName = this.annotationClass.java.name
    return fqName == targetFqName
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

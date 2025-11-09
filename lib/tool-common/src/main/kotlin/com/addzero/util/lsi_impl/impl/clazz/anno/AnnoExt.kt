package com.addzero.util.lsi_impl.impl.clazz.anno

import com.addzero.util.lsi.constant.COMMENT_ANNOTATION_METHOD_MAP


// 提取公共逻辑：通过反射调用注解方法获取字符串值
fun Annotation.getArg(methodName: String): String? {
    return try {
        val method = this.annotationClass.java.getDeclaredMethod(methodName)
        method.invoke(this) as? String
    } catch (e: Exception) {
        null
    }
}

fun Array<Annotation>.comment(): String? {
    this.forEach { annotation ->
        val annotationName = annotation.annotationClass.java.name
        val methodName = COMMENT_ANNOTATION_METHOD_MAP[annotationName] ?: return null
        val description = annotation.getArg(methodName)
        if (!description.isNullOrBlank()) {
            return description
        }
    }
    return null
}

fun Annotation.attributes(): Map<String, Any?> {
    val associate = this.annotationClass.java.declaredMethods.associate { method ->
        method.name to try {
            method.invoke(this)
        } catch (e: Exception) {
            null
        }
    }
    return associate
}

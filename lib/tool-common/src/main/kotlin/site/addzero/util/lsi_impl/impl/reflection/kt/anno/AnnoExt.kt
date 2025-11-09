package site.addzero.util.lsi_impl.impl.reflection.kt.anno

import site.addzero.util.lsi.constant.COMMENT_ANNOTATION_METHOD_MAP


// 提取公共逻辑：通过反射调用注解方法获取字符串值
fun Annotation.getArg(methodName: String): String? {
    return try {
        val method = this.annotationClass.java.getDeclaredMethod(methodName)
        method.invoke(this) as? String
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun Annotation.getArg(): String? {
    return getArg("value")
}

fun Array<Annotation>.fieldComment(): String? {
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

fun Array<Annotation>.guessTableNameOrNull(): String? {
    for (annotation in this) {
        val qualifiedName = annotation.annotationClass.java.name
        val arg = annotation.getArg("name")
        when (qualifiedName) {
            "com.baomidou.mybatisplus.annotation.TableName" -> {
                // MyBatis Plus 的 @TableName 注解
                return annotation.getArg()

            }

            "org.babyfish.jimmer.sql.Table" -> {
                // Jimmer 的 @Table 注解
                return arg
            }

            "javax.persistence.Table",
            "jakarta.persistence.Table" -> {
                // JPA 的 @Table 注解
                return arg
            }
        }
    }
    return null
}

fun Annotation.isTargetAnnotation(targetFqName: String): Boolean {
    val fqName = this.annotationClass.java.name
    return fqName == targetFqName
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

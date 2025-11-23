package site.addzero.util.lsi.method

/**
 * 检查方法是否具有指定的注解
 * @param annotationNames 注解全限定名数组
 * @return 如果方法具有其中任何一个注解，则返回true，否则返回false
 */
fun LsiMethod.hasAnnotation(vararg annotationNames: String): Boolean {
    return annotationNames.any { annotationName ->
        annotations.any { annotation ->
            annotation.qualifiedName == annotationName
        }
    }
}

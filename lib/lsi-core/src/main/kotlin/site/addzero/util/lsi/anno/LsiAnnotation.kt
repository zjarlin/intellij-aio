package site.addzero.util.lsi.anno

/**
 * 语言无关的注解结构抽象接口
 * Lsi = Language Structure Interface
 */
interface LsiAnnotation {
    /**
     * 获取注解的全限定名
     */
    val qualifiedName: String?

    /**
     * 获取注解的简单名称
     */
    val simpleName: String?

    /**
     * 获取注解的所有属性值
     */
    val attributes: Map<String, Any?>

    /**
     * 根据属性名获取注解属性值
     */
    fun getAttribute(name: String): Any?

    /**
     * 判断是否包含指定名称的属性
     */
    fun hasAttribute(name: String): Boolean
}

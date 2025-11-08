package com.addzero.util.lsi

/**
 * 语言无关的字段结构抽象接口
 * Lsi = Language Structure Interface
 */
interface LsiField {
    /**
     * 获取字段名称
     */
    val name: String?

    /**
     * 获取字段类型
     */
    val type: LsiType?

    /**
     * 获取字段类型名称
     */
    val typeName: String?

    /**
     * 获取字段注释
     */
    val comment: String?

    /**
     * 获取字段上的注解
     */
    val annotations: List<LsiAnnotation>

    /**
     * 判断是否为静态字段
     */
    val isStatic: Boolean

    /**
     * 判断是否为常量字段
     */
    val isConstant: Boolean

    /**
     * 判断是否为可变字段（Kotlin 的 var）
     * 对于 Java 字段，如果没有 final 修饰符则认为是可变的
     */
    val isVar: Boolean

    /**
     * 判断是否为延迟初始化字段（Kotlin 的 lateinit）
     * 对于 Java 字段，始终返回 false
     */
    val isLateInit: Boolean

    /**
     * 判断是否为集合类型
     */
    val isCollectionType: Boolean

    /**
     * 获取字段的默认值（如果有的话）
     */
    val defaultValue: String?
    
    /**
     * 判断字段是否为集合类型
     */
    fun isCollectionType(): Boolean = isCollectionType

    /**
     * 判断是否为数据库字段
     * 数据库字段需要满足：非静态 && 非集合类型
     */
    val isDbField: Boolean
        get() = !isStatic && !isCollectionType

    /**
     * 获取数据库列名
     * 优先从注解中获取，如果没有则返回 null
     * 支持的注解：
     * - Jimmer: @Column(name = "xxx")
     * - MyBatis Plus: @TableField(value = "xxx")
     */
    val columnName: String?

    /**
     * 获取声明该字段的类
     */
    val declaringClass: LsiClass?
    
    /**
     * 获取字段类型对应的LsiClass（如果是对象类型）
     */
    val fieldTypeClass: LsiClass?
    
    /**
     * 判断是否为嵌套对象
     */
    val isNestedObject: Boolean
    
    /**
     * 获取嵌套字段信息（如果该字段是对象类型）
     */
    val children: List<LsiField>

    /**
     * 检查字段是否具有指定的注解
     * @param annotationNames 注解全限定名数组
     * @return 如果字段具有其中任何一个注解，则返回true，否则返回false
     */
    fun hasAnnotation(vararg annotationNames: String): Boolean {
        return annotationNames.any { annotationName ->
            annotations.any { annotation ->
                annotation.qualifiedName == annotationName
            }
        }
    }
}
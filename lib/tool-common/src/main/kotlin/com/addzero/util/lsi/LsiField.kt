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
}
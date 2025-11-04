package com.addzero.util.lsi

/**
 * 语言无关的类结构抽象接口
 * Lsi = Language Structure Interface
 */
interface LsiClass {
    /**
     * 获取类的简单名称
     */
    val name: String?

    /**
     * 获取类的全限定名
     */
    val qualifiedName: String?

    /**
     * 获取类的注释
     */
    val comment: String?

    /**
     * 获取类的所有字段
     */
    val fields: List<LsiField>

    /**
     * 获取类上的注解
     */
    val annotations: List<LsiAnnotation>

    /**
     * 判断是否为接口
     */
    val isInterface: Boolean

    /**
     * 判断是否为枚举
     */
    val isEnum: Boolean

    /**
     * 判断是否为集合类型
     */
    val isCollectionType: Boolean

    /**
     * 获取父类
     */
    val superClasses: List<LsiClass>

    /**
     * 获取实现的接口
     */
    val interfaces: List<LsiClass>
}

/**
 * 字段信息数据类，包含字段及其嵌套信息
 */
data class FieldInfo(
    val declaringClass: LsiClass,
    val field: LsiField,
    val description: String?,
    val fieldType: LsiClass?,
    val isNestedObject: Boolean,
    val children: List<FieldInfo> = emptyList()
) {
    fun toSimpleString(): String {
        return "${field.name}: ${fieldType?.name ?: field.typeName}${if (description != null) " ($description)" else ""}"
    }
}
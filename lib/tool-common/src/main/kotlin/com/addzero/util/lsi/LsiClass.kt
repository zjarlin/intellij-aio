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


    /**
     * 检查类是否具有指定的注解
     * @param annotationNames 注解全限定名数组
     * @return 如果类具有其中任何一个注解，则返回true，否则返回false
     */
    fun hasAnnotation(vararg annotationNames: String): Boolean {
        return annotationNames.any { annotationName ->
            annotations.any { annotation ->
                annotation.qualifiedName == annotationName
            }
        }
    }

    /**
     * 获取数据库字段列表
     * 过滤掉静态字段、集合类型字段
     * @return 数据库字段列表
     */
    val dbFields: List<LsiField>
        get() = fields.filter { it.isDbField }

    /**
     * 获取所有数据库字段（包括继承的字段）
     * 这个方法会递归获取父类的字段
     * @return 所有数据库字段列表
     */
    fun getAllDbFields(): List<LsiField> {
        val result = mutableListOf<LsiField>()

        // 添加当前类的数据库字段
        result.addAll(dbFields)

        // 递归添加父类的数据库字段
        superClasses.forEach { superClass ->
            result.addAll(superClass.getAllDbFields())
        }

        return result
    }

    val guessTableName: String
    val methods: List<LsiMethod>
}

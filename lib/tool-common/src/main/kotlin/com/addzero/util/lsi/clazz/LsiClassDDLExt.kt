package com.addzero.util.lsi.clazz

import com.addzero.util.lsi.field.LsiField


/**
 * 获取数据库字段列表
 * 过滤掉静态字段、集合类型字段
 * @return 数据库字段列表
 */
val LsiClass.dbFields: List<LsiField> get() = fields.filter { it.isDbField }
/**
 * 获取所有数据库字段（包括继承的字段）
 * 这个方法会递归获取父类的字段
 * @return 所有数据库字段列表
 */
fun LsiClass.getAllDbFields(): List<LsiField> {
    val result = mutableListOf<LsiField>()

    // 添加当前类的数据库字段
    result.addAll(dbFields)

    // 递归添加父类的数据库字段
    superClasses.forEach { superClass ->
        result.addAll(superClass.getAllDbFields())
    }

    return result
}

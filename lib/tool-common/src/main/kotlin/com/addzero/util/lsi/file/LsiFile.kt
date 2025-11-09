package com.addzero.util.lsi.file

import com.addzero.util.lsi.anno.LsiAnnotation
import com.addzero.util.lsi.clazz.LsiClass

/**
 * 语言无关的文件结构抽象接口
 * Lsi = Language Structure Interface
 */
interface LsiFile {
    /**
     * 获取文件路径
     */
    val filePath: String?

    /**
     * 获取包名
     */
    val packageName: String?

    /**
     * 获取文件中定义的所有类
     */
    val classes: List<LsiClass>

    /**
     * 根据类名查找类
     */
    fun findClassByName(name: String): LsiClass?

    /**
     * 获取文件的注释
     */
    val comment: String?

    /**
     * 获取文件上的注解
     */
    val annotations: List<LsiAnnotation>
}

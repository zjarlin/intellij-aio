package site.addzero.util.lsi.project

import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.file.LsiFile

/**
 * 语言无关的项目结构抽象接口
 * Lsi = Language Structure Interface
 */
interface LsiProject {
    /**
     * 获取项目名称
     */
    val name: String?

    /**
     * 获取项目根路径
     */
    val basePath: String?

    /**
     * 获取项目中所有的文件
     */
    val files: List<LsiFile>

    /**
     * 根据文件路径查找文件
     */
    fun findFileByPath(path: String): LsiFile?

    /**
     * 根据类名查找类（全局搜索）
     */
    fun findClassByName(name: String): LsiClass?

    /**
     * 根据注解名称查找所有带有该注解的类
     */
    fun findClassesByAnnotation(annotationName: String): List<LsiClass>

    /**
     * 获取项目中的所有类
     */
    val classes: List<LsiClass>
}

package site.addzero.util.ddlgenerator.inter

import site.addzero.util.lsi.clazz.LsiClass

/**
 * 表上下文接口，由用户实现
 * 用于提供表定义信息和解析元数据
 */
interface TableContext {
    /**
     * 获取所有 LSI 类
     */
    fun getLsiClasses(): List<LsiClass>
    
    /**
     * 根据表名获取 LSI 类
     */
    fun getLsiClass(tableName: String): LsiClass?
    
    /**
     * 获取表之间的依赖关系
     * 返回一个映射，键是表名，值是该表依赖的其他表的列表
     */
    fun getTableDependencies(): Map<String, List<String>>
    
    /**
     * 根据表名获取依赖该表的其他表
     */
    fun getDependentTables(tableName: String): List<String>
}
package site.addzero.util.ddlgenerator.inter

import site.addzero.util.lsi.clazz.LsiClass

/**
 * 元数据提取器接口
 * 用户可以实现此接口来从自己的注解或配置中提取表定义信息
 */
interface MetadataExtractor {
    /**
     * 从元数据中提取 LSI 类
     */
    fun extractLsiClass(): LsiClass
    
    /**
     * 提取表之间的依赖关系
     */
    fun extractDependencies(): List<String>
}
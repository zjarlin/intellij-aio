package site.addzero.util.ddlgenerator

import site.addzero.util.ddlgenerator.inter.MetadataExtractor
import site.addzero.util.ddlgenerator.inter.TableContext
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.clazz.guessTableName

/**
 * 基于元数据提取器的表上下文实现
 * 用户可以通过提供MetadataExtractor列表来创建TableContext实例
 */
class MetadataTableContext(private val extractors: List<MetadataExtractor>) : TableContext {
    private val lsiClasses: List<LsiClass> by lazy { extractors.map { it.extractLsiClass() } }
    private val tableNameToLsiClass: Map<String, LsiClass> by lazy { 
        lsiClasses.associateBy { it.guessTableName } 
    }
    
    override fun getLsiClasses(): List<LsiClass> = lsiClasses
    
    override fun getLsiClass(tableName: String): LsiClass? {
        return tableNameToLsiClass[tableName]
    }
    
    override fun getTableDependencies(): Map<String, List<String>> {
        val dependencies = mutableMapOf<String, List<String>>()
        for (extractor in extractors) {
            val lsiClass = extractor.extractLsiClass()
            dependencies[lsiClass.guessTableName] = extractor.extractDependencies()
        }
        return dependencies
    }
    
    override fun getDependentTables(tableName: String): List<String> {
        val dependents = mutableListOf<String>()
        for (extractor in extractors) {
            val lsiClass = extractor.extractLsiClass()
            if (extractor.extractDependencies().contains(tableName)) {
                dependents.add(lsiClass.guessTableName)
            }
        }
        return dependents
    }
    
    companion object {
        /**
         * 从元数据提取器列表创建TableContext实例
         */
        fun fromExtractors(extractors: List<MetadataExtractor>): TableContext {
            return MetadataTableContext(extractors)
        }
    }
}
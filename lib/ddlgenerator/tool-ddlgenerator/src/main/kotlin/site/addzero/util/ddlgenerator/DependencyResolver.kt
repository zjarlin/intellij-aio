package site.addzero.util.ddlgenerator

import site.addzero.util.ddlgenerator.inter.TableContext
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.clazz.guessTableName

/**
 * 依赖解析器
 * 用于解析表之间的依赖关系，确保正确的创建顺序
 */
class DependencyResolver {
    
    /**
     * 根据依赖关系对表进行排序
     * 使用拓扑排序算法确保依赖表在被依赖表之前创建
     */
    fun resolveCreationOrder(context: TableContext): List<LsiClass> {
        val dependencies = context.getTableDependencies()
        val lsiClasses = context.getLsiClasses()
        val tableNameToLsiClass = lsiClasses.associateBy { it.guessTableName }
        
        // 构建依赖图
        val graph = mutableMapOf<String, MutableList<String>>()
        for (lsiClass in lsiClasses) {
            val tableName = lsiClass.guessTableName
            graph[tableName] = dependencies[tableName]?.toMutableList() ?: mutableListOf()
        }
        
        // 拓扑排序
        val result = mutableListOf<LsiClass>()
        val visited = mutableSetOf<String>()
        val tempMark = mutableSetOf<String>()
        
        fun visit(node: String) {
            if (tempMark.contains(node)) {
                throw IllegalStateException("Circular dependency detected involving table: $node")
            }
            
            if (!visited.contains(node)) {
                tempMark.add(node)
                val dependencies = graph[node] ?: emptyList()
                for (dependency in dependencies) {
                    visit(dependency)
                }
                tempMark.remove(node)
                visited.add(node)
                
                tableNameToLsiClass[node]?.let { result.add(it) }
            }
        }
        
        for (lsiClass in lsiClasses) {
            val tableName = lsiClass.guessTableName
            if (!visited.contains(tableName)) {
                visit(tableName)
            }
        }
        
        return result
    }
    
    /**
     * 获取删除顺序（与创建顺序相反）
     */
    fun resolveDeletionOrder(context: TableContext): List<LsiClass> {
        return resolveCreationOrder(context).reversed()
    }
}
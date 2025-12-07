package site.addzero.util.ddlgenerator

import site.addzero.util.ddlgenerator.model.*
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap

/**
 * DDL生成器工厂类
 * 提供更便捷的方式来创建DDL生成器实例
 */
object DdlGeneratorFactory {
    private val strategyCache = ConcurrentHashMap<Dialect, DdlGenerationStrategy>()
    private val allStrategies: List<DdlGenerationStrategy> by lazy {
        ServiceLoader.load(DdlGenerationStrategy::class.java).toList()
    }
    
    /**
     * 根据数据库方言创建DDL生成器
     */
    fun create(dialect: Dialect): DdlGenerator {
        return DdlGenerator(getOrCreateStrategy(dialect))
    }
    
    /**
     * 根据数据库方言名称创建DDL生成器
     */
    fun create(dialectName: String): DdlGenerator {
        val dialect = try {
            Dialect.valueOf(dialectName.uppercase())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Unsupported dialect: $dialectName")
        }
        return create(dialect)
    }
    
    /**
     * 获取或创建方言策略实现（带缓存）
     */
    private fun getOrCreateStrategy(dialect: Dialect): DdlGenerationStrategy {
        return strategyCache.getOrPut(dialect) {
            createStrategyForDialect(dialect)
        }
    }
    
    /**
     * 使用ServiceLoader加载方言策略实现
     */
    private fun createStrategyForDialect(dialect: Dialect): DdlGenerationStrategy {
        // 尝试通过ServiceLoader加载支持该方言的实现
        for (strategy in allStrategies) {
            if (strategy.supports(dialect)) {
                return strategy
            }
        }
        
        // 如果ServiceLoader没有找到合适的实现，则使用默认实现
        return when (dialect) {
            Dialect.MYSQL -> MySqlDdlGenerationStrategy()
            Dialect.POSTGRESQL -> PostgreSqlDdlGenerationStrategy()
            else -> throw NotImplementedError("Dialect $dialect not yet implemented")
        }
    }
}
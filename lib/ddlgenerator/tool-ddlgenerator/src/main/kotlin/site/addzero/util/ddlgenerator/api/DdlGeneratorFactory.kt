package site.addzero.util.ddlgenerator.api

import site.addzero.util.db.DatabaseType
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap

/**
 * DDL生成器工厂 - 使用SPI机制自动发现策略实现
 *
 * 通过 ServiceLoader 加载所有 DdlGenerationStrategy 实现，
 * 并根据数据库方言自动选择合适的策略
 *
 * 注意：通常不需要直接使用此工厂，推荐使用 LsiClass 和 LsiField 的扩展函数
 */
internal object DdlGeneratorFactory {
    private val strategyCache = ConcurrentHashMap<DatabaseType, DdlGenerationStrategy>()

    /**
     * 懒加载所有通过 ServiceLoader 注册的策略实现
     */
    private val allStrategies: List<DdlGenerationStrategy> by lazy {
        ServiceLoader.load(DdlGenerationStrategy::class.java).toList().also {
            println("Loaded ${it.size} DDL generation strategies via ServiceLoader")
        }
    }

    /**
     * 根据数据库方言获取策略
     *
     * @param dialect 数据库方言
     * @return DDL生成策略实例
     * @throws IllegalArgumentException 如果找不到支持该方言的策略
     */
    fun getStrategy(dialect: DatabaseType): DdlGenerationStrategy {
        return getOrCreateStrategy(dialect)
    }

    /**
     * 根据数据库方言名称获取策略
     *
     * @param dialectName 方言名称（不区分大小写）
     * @return DDL生成策略实例
     * @throws IllegalArgumentException 如果方言名称无效或找不到支持该方言的策略
     */
    fun getStrategy(dialectName: String): DdlGenerationStrategy {
        val dialect = try {
            DatabaseType.valueOf(dialectName.uppercase())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Unknown dialect: $dialectName. Available: ${DatabaseType.values().joinToString()}", e)
        }
        return getStrategy(dialect)
    }

    /**
     * 获取或创建方言策略实现（带缓存）
     */
    private fun getOrCreateStrategy(dialect: DatabaseType): DdlGenerationStrategy {
        return strategyCache.getOrPut(dialect) {
            findStrategyForDialect(dialect)
        }
    }

    /**
     * 使用ServiceLoader查找支持指定方言的策略
     *
     * @param dialect 数据库方言
     * @return 支持该方言的策略实现
     * @throws IllegalArgumentException 如果找不到支持该方言的策略
     */
    private fun findStrategyForDialect(dialect: DatabaseType): DdlGenerationStrategy {
        // 通过ServiceLoader查找第一个支持该方言的策略
        val strategy = allStrategies.firstOrNull { it.supports(dialect) }
        if (strategy != null) {
            return strategy
        }

        // 如果ServiceLoader没有找到，抛出异常
        throw IllegalArgumentException(
            "No DDL generation strategy found for dialect: $dialect. " +
            "Please ensure the strategy is registered via META-INF/services/site.addzero.util.ddlgenerator.DdlGenerationStrategy"
        )
    }

    /**
     * 获取所有已注册的方言
     */
    fun getSupportedDialects(): Set<DatabaseType> {
        return allStrategies.flatMap { strategy ->
            DatabaseType.values().filter { strategy.supports(it) }
        }.toSet()
    }
}

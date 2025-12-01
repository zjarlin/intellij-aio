package site.addzero.ddl.sql

import site.addzero.ddl.sql.dialect.*

/**
 * 方言初始化器
 * 
 * 在模块加载时自动注册所有方言
 */
@Suppress("unused")
object DialectInitializer {
    
    /**
     * 初始化所有方言
     */
    fun initialize() {
        SqlDialectRegistry.register(MysqlDialect())
        SqlDialectRegistry.register(PostgresqlDialect())
        SqlDialectRegistry.register(OracleDialect())
        SqlDialectRegistry.register(DmDialect())
        SqlDialectRegistry.register(H2Dialect())
        SqlDialectRegistry.register(TdengineDialect())
    }
    
    // 使用init块确保在类加载时就初始化
    init {
        initialize()
    }
}

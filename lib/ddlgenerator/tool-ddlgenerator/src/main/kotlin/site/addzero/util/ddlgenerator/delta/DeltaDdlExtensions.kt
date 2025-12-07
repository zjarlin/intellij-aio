package site.addzero.util.ddlgenerator.delta

import site.addzero.entity.JdbcTableMetadata
import site.addzero.util.db.DatabaseType
import site.addzero.util.ddlgenerator.api.DdlGeneratorFactory
import site.addzero.util.ddlgenerator.diff.comparator.DefaultTableComparator
import site.addzero.util.ddlgenerator.diff.model.DiffConfig
import site.addzero.util.ddlgenerator.diff.model.SchemaDiff
import site.addzero.util.lsi.clazz.LsiClass

/**
 * 生成差量 DDL（便捷方法）
 */
fun List<LsiClass>.generateDeltaDdl(
    dbTables: List<JdbcTableMetadata>,
    databaseType: DatabaseType = DatabaseType.MYSQL,
    config: DiffConfig = DiffConfig()
): String {
    val comparator = DefaultTableComparator()
    val diff = comparator.compareSchema(this, dbTables, config)
    
    val strategy = DdlGeneratorFactory.getStrategy(databaseType)
    val generator = DeltaDdlGenerator(strategy)
    
    return generator.generateDeltaDdl(diff)
}

/**
 * 对比差异（不生成 SQL）
 */
fun List<LsiClass>.compareTo(
    dbTables: List<JdbcTableMetadata>,
    config: DiffConfig = DiffConfig()
): SchemaDiff {
    val comparator = DefaultTableComparator()
    return comparator.compareSchema(this, dbTables, config)
}

/**
 * 生成差量 DDL（从差异对象）
 */
fun SchemaDiff.toDeltaDdl(databaseType: DatabaseType = DatabaseType.MYSQL): String {
    val strategy = DdlGeneratorFactory.getStrategy(databaseType)
    val generator = DeltaDdlGenerator(strategy)
    return generator.generateDeltaDdl(this)
}

package site.addzero.util.ddlgenerator.extension

import site.addzero.util.db.DatabaseType
import site.addzero.util.ddlgenerator.api.DdlGeneratorFactory
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.clazz.guessTableName
import site.addzero.util.lsi.database.getDatabaseForeignKeys
import site.addzero.util.lsi.database.getIndexDefinitions
import site.addzero.util.lsi.database.scanManyToManyTables

/**
 * 生成完整的Schema DDL（包括表、索引、中间表、外键）
 * 
 * DDL生成顺序（重要！）：
 * 1. 创建所有实体表（不含外键）
 * 2. 创建多对多中间表（不含外键）
 * 3. 创建索引
 * 4. 添加所有外键约束（实体表 + 中间表）
 * 5. 添加注释
 * 
 * @param dialect 数据库方言
 * @param includeIndexes 是否包含索引DDL
 * @param includeManyToManyTables 是否包含多对多中间表DDL
 * @param includeForeignKeys 是否包含外键约束DDL
 */
fun List<LsiClass>.toCompleteSchemaDDL(
    dialect: DatabaseType,
    includeIndexes: Boolean = true,
    includeManyToManyTables: Boolean = true,
    includeForeignKeys: Boolean = true
): String {
    val strategy = DdlGeneratorFactory.getStrategy(dialect)
    val statements = mutableListOf<String>()
    
    // ===== 第一阶段：创建所有表（不含外键） =====
    statements.add("-- =============================================")
    statements.add("-- Phase 1: Create All Tables (without FK)")
    statements.add("-- =============================================")
    statements.add("")
    
    // 1.1 创建实体表
    this.forEach { lsiClass ->
        statements.add("-- Table: ${lsiClass.name}")
        statements.add(strategy.generateCreateTable(lsiClass))
        statements.add("")
    }
    
    // 1.2 创建多对多中间表
    val manyToManyTables = if (includeManyToManyTables) {
        this.scanManyToManyTables()
    } else {
        emptyList()
    }
    
    if (manyToManyTables.isNotEmpty()) {
        statements.add("-- Many-to-Many Junction Tables")
        manyToManyTables.forEach { table ->
            statements.add("-- Junction: ${table.leftTableName} <-> ${table.rightTableName}")
            statements.add(strategy.generateManyToManyTable(table))
            statements.add("")
        }
    }
    
    // ===== 第二阶段：创建索引 =====
    if (includeIndexes) {
        statements.add("-- =============================================")
        statements.add("-- Phase 2: Create Indexes")
        statements.add("-- =============================================")
        statements.add("")
        
        this.forEach { lsiClass ->
            val indexes = lsiClass.getIndexDefinitions()
            if (indexes.isNotEmpty()) {
                statements.add("-- Indexes for ${lsiClass.name}")
                indexes.forEach { index ->
                    statements.add(strategy.generateCreateIndex(lsiClass.guessTableName, index))
                }
                statements.add("")
            }
        }
    }
    
    // ===== 第三阶段：添加所有外键约束 =====
    if (includeForeignKeys) {
        statements.add("-- =============================================")
        statements.add("-- Phase 3: Add Foreign Key Constraints")
        statements.add("-- =============================================")
        statements.add("")
        
        // 3.1 实体表的外键（从@JoinColumn等注解）
        this.forEach { lsiClass ->
            val foreignKeys = lsiClass.getDatabaseForeignKeys()
            if (foreignKeys.isNotEmpty()) {
                statements.add("-- Foreign Keys for ${lsiClass.name}")
                foreignKeys.forEach { fk ->
                    statements.add(strategy.generateAddForeignKey(lsiClass.guessTableName, fk))
                }
                statements.add("")
            }
        }
        
        // 3.2 中间表的外键
        if (manyToManyTables.isNotEmpty()) {
            statements.add("-- Foreign Keys for Junction Tables")
            manyToManyTables.forEach { table ->
                statements.add("-- Foreign Keys for ${table.tableName}")
                strategy.generateManyToManyTableForeignKeys(table).forEach { fkSql ->
                    statements.add(fkSql)
                }
                statements.add("")
            }
        }
    }
    
    // ===== 第四阶段：添加注释 =====
    statements.add("-- =============================================")
    statements.add("-- Phase 4: Add Comments")
    statements.add("-- =============================================")
    statements.add("")
    
    this.forEach { lsiClass ->
        if (lsiClass.comment != null || lsiClass.fields.any { it.comment != null }) {
            statements.add(strategy.generateAddComment(lsiClass))
            statements.add("")
        }
    }
    
    return statements.joinToString("\n")
}

/**
 * 生成完整的Schema DDL（字符串方言）
 */
fun List<LsiClass>.toCompleteSchemaDDL(
    dialectName: String,
    includeIndexes: Boolean = true,
    includeManyToManyTables: Boolean = true,
    includeForeignKeys: Boolean = true
): String {
    val dialect = DatabaseType.valueOf(dialectName.uppercase())
    return toCompleteSchemaDDL(dialect, includeIndexes, includeManyToManyTables, includeForeignKeys)
}

/**
 * 仅生成索引DDL
 */
fun LsiClass.toIndexesDDL(dialect: DatabaseType): String {
    val strategy = DdlGeneratorFactory.getStrategy(dialect)
    val indexes = this.getIndexDefinitions()
    
    if (indexes.isEmpty()) {
        return "-- No indexes defined for ${this.name}"
    }
    
    val tableName = this.guessTableName
    return indexes.joinToString("\n") { index ->
        strategy.generateCreateIndex(tableName, index)
    }
}

/**
 * 仅生成多对多中间表DDL
 */
fun List<LsiClass>.toManyToManyTablesDDL(dialect: DatabaseType): String {
    val strategy = DdlGeneratorFactory.getStrategy(dialect)
    val tables = this.scanManyToManyTables()
    
    if (tables.isEmpty()) {
        return "-- No many-to-many relationships found"
    }
    
    return tables.joinToString("\n\n") { table ->
        "-- ${table.leftTableName} <-> ${table.rightTableName}\n" +
        strategy.generateManyToManyTable(table)
    }
}

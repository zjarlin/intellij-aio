package site.addzero.util.ddlgenerator.delta

import site.addzero.util.ddlgenerator.api.DdlGenerationStrategy
import site.addzero.util.ddlgenerator.diff.model.SchemaDiff
import site.addzero.util.ddlgenerator.diff.model.TableDiff

/**
 * 差量 DDL 生成器
 * 根据差异模型生成增量 SQL 语句
 */
class DeltaDdlGenerator(
    private val strategy: DdlGenerationStrategy
) {
    
    /**
     * 生成 Schema 差量 DDL
     */
    fun generateDeltaDdl(diff: SchemaDiff): String {
        if (!diff.hasChanges) {
            return "-- No changes detected"
        }
        
        val statements = mutableListOf<String>()
        
        // 1. 新增表
        diff.newTables.forEach { lsiClass ->
            statements.add("-- Create new table: ${lsiClass.name}")
            statements.add(strategy.generateCreateTable(lsiClass))
        }
        
        // 2. 修改表
        diff.modifiedTables.forEach { modifiedTableInfo ->
            val tableDiff = modifiedTableInfo.diff
            val lsiClass = modifiedTableInfo.lsiClass
            
            statements.add("-- Modify table: ${tableDiff.tableName}")
            
            // 2.1 新增列
            tableDiff.addedColumns.forEach { field ->
                statements.add(strategy.generateAddColumn(tableDiff.tableName, field))
            }
            
            // 2.2 修改列
            tableDiff.modifiedColumns.forEach { modification ->
                statements.add(
                    "-- Modify column: ${modification.field.name} " +
                    "(changes: ${modification.changes.joinToString(", ")})"
                )
                statements.add(strategy.generateModifyColumn(tableDiff.tableName, modification.field))
            }
            
            // 2.3 删除列（如果允许）
            if (diff.config.allowDrop && tableDiff.droppedColumns.isNotEmpty()) {
                tableDiff.droppedColumns.forEach { column ->
                    statements.add(strategy.generateDropColumn(tableDiff.tableName, column.columnName))
                }
            }
        }
        
        // 3. 删除表（如果允许）
        if (diff.config.allowDrop && diff.droppedTables.isNotEmpty()) {
            diff.droppedTables.forEach { tableName ->
                statements.add("-- Drop table: $tableName")
                statements.add(strategy.generateDropTable(tableName))
            }
        }
        
        return statements.joinToString("\n\n")
    }
    
    /**
     * 生成单个表的差量 DDL
     */
    fun generateTableDeltaDdl(diff: TableDiff): String {
        return when (diff) {
            is TableDiff.NewTable -> {
                buildString {
                    appendLine("-- Create new table: ${diff.lsiClass.name}")
                    append(strategy.generateCreateTable(diff.lsiClass))
                }
            }
            
            is TableDiff.DroppedTable -> {
                buildString {
                    appendLine("-- Drop table: ${diff.tableName}")
                    append(strategy.generateDropTable(diff.tableName))
                }
            }
            
            is TableDiff.ModifiedTable -> {
                buildString {
                    appendLine("-- Modify table: ${diff.tableName}")
                    
                    // 新增列
                    diff.addedColumns.forEach { field ->
                        appendLine(strategy.generateAddColumn(diff.tableName, field))
                    }
                    
                    // 修改列
                    diff.modifiedColumns.forEach { modification ->
                        appendLine(
                            "-- Modify column: ${modification.field.name} " +
                            "(changes: ${modification.changes.joinToString(", ")})"
                        )
                        appendLine(strategy.generateModifyColumn(diff.tableName, modification.field))
                    }
                    
                    // 删除列
                    diff.droppedColumns.forEach { column ->
                        appendLine(strategy.generateDropColumn(diff.tableName, column.columnName))
                    }
                }
            }
            
            is TableDiff.NoChange -> "-- No changes"
        }
    }
}

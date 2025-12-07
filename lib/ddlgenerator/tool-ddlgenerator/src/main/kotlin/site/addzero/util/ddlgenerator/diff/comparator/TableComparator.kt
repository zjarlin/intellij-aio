package site.addzero.util.ddlgenerator.diff.comparator

import site.addzero.util.ddlgenerator.diff.matcher.ColumnMatcher
import site.addzero.util.ddlgenerator.diff.model.*

import site.addzero.entity.JdbcColumnMetadata
import site.addzero.entity.JdbcTableMetadata
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.clazz.guessTableName
import site.addzero.util.lsi.database.databaseFields

/**
 * 表比对器接口
 */
interface TableComparator {
    /**
     * 对比单个表
     */
    fun compare(lsiClass: LsiClass, dbTable: JdbcTableMetadata?, config: DiffConfig = DiffConfig()): TableDiff
    
    /**
     * 对比整个 Schema
     */
    fun compareSchema(
        lsiClasses: List<LsiClass>,
        dbTables: List<JdbcTableMetadata>,
        config: DiffConfig = DiffConfig()
    ): SchemaDiff
}

/**
 * 默认表比对器实现
 */
class DefaultTableComparator : TableComparator {
    
    override fun compare(lsiClass: LsiClass, dbTable: JdbcTableMetadata?, config: DiffConfig): TableDiff {
        // 如果数据库表不存在，返回新增表
        if (dbTable == null) {
            return TableDiff.NewTable(lsiClass)
        }
        
        val tableName = lsiClass.guessTableName
        val lsiFields = lsiClass.databaseFields
        val dbColumns = dbTable.columns
        
        // 按名称建立映射（忽略大小写）
        val dbColumnMap = dbColumns.associateBy { 
            if (config.ignoreCase) it.columnName.lowercase() else it.columnName
        }
        val lsiFieldMap = lsiFields.associateBy { field ->
            val name = field.columnName ?: field.name ?: ""
            if (config.ignoreCase) name.lowercase() else name
        }
        
        // 找出新增的列
        val addedColumns = lsiFields.filter { field ->
            val name = field.columnName ?: field.name ?: ""
            val key = if (config.ignoreCase) name.lowercase() else name
            !dbColumnMap.containsKey(key)
        }
        
        // 找出删除的列
        val droppedColumns = if (config.allowDrop) {
            dbColumns.filter { column ->
                val key = if (config.ignoreCase) column.columnName.lowercase() else column.columnName
                !lsiFieldMap.containsKey(key)
            }
        } else {
            emptyList()
        }
        
        // 找出修改的列
        val modifiedColumns = lsiFields.mapNotNull { field ->
            val name = field.columnName ?: field.name ?: ""
            val key = if (config.ignoreCase) name.lowercase() else name
            val dbColumn = dbColumnMap[key] ?: return@mapNotNull null
            
            val changes = ColumnMatcher.detectChanges(field, dbColumn, config)
            if (changes.isNotEmpty()) {
                ColumnModification(field, dbColumn, changes)
            } else {
                null
            }
        }
        
        // 如果有任何变化，返回修改表
        if (addedColumns.isNotEmpty() || droppedColumns.isNotEmpty() || modifiedColumns.isNotEmpty()) {
            return TableDiff.ModifiedTable(
                tableName = tableName,
                schema = dbTable.schema,
                addedColumns = addedColumns,
                droppedColumns = droppedColumns,
                modifiedColumns = modifiedColumns
            )
        }
        
        return TableDiff.NoChange
    }
    
    override fun compareSchema(
        lsiClasses: List<LsiClass>,
        dbTables: List<JdbcTableMetadata>,
        config: DiffConfig
    ): SchemaDiff {
        // 按表名建立映射
        val dbTableMap = dbTables.associateBy { 
            if (config.ignoreCase) it.tableName.lowercase() else it.tableName
        }
        val lsiClassMap = lsiClasses.associateBy { lsiClass ->
            val tableName = lsiClass.guessTableName
            if (config.ignoreCase) tableName.lowercase() else tableName
        }
        
        // 新增的表
        val newTables = lsiClasses.filter { lsiClass ->
            val tableName = lsiClass.guessTableName
            val key = if (config.ignoreCase) tableName.lowercase() else tableName
            !dbTableMap.containsKey(key)
        }
        
        // 删除的表
        val droppedTables = if (config.allowDrop) {
            dbTables
                .filter { dbTable ->
                    val key = if (config.ignoreCase) dbTable.tableName.lowercase() else dbTable.tableName
                    !lsiClassMap.containsKey(key)
                }
                .map { it.tableName }
        } else {
            emptyList()
        }
        
        // 修改的表
        val modifiedTables = lsiClasses.mapNotNull { lsiClass ->
            val tableName = lsiClass.guessTableName
            val key = if (config.ignoreCase) tableName.lowercase() else tableName
            val dbTable = dbTableMap[key] ?: return@mapNotNull null
            
            val diff = compare(lsiClass, dbTable, config)
            when (diff) {
                is TableDiff.ModifiedTable -> if (diff.hasChanges) {
                    ModifiedTableInfo(lsiClass, diff)
                } else null
                else -> null
            }
        }
        
        return SchemaDiff(
            newTables = newTables,
            droppedTables = droppedTables,
            modifiedTables = modifiedTables,
            config = config
        )
    }
}

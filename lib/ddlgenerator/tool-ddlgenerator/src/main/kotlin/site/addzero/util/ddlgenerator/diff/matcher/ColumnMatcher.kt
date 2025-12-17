package site.addzero.util.ddlgenerator.diff.matcher

import site.addzero.util.ddlgenerator.diff.model.ColumnModification
import site.addzero.util.ddlgenerator.diff.model.DiffConfig

import site.addzero.entity.JdbcColumnMetadata
import site.addzero.util.lsi.database.DatabaseColumnType
import site.addzero.util.lsi.database.getDatabaseColumnType
import site.addzero.util.lsi.database.guessLength
import site.addzero.util.lsi.field.LsiField

/**
 * 列类型匹配器
 * 负责比对 LSI 字段和数据库列的类型、属性等
 */
object ColumnMatcher {
    
    /**
     * 判断列是否匹配（类型、长度、可空性等）
     */
    fun isColumnMatched(
        field: LsiField,
        dbColumn: JdbcColumnMetadata,
        config: DiffConfig = DiffConfig()
    ): Boolean {
        val changes = detectChanges(field, dbColumn, config)
        return changes.isEmpty()
    }
    
    /**
     * 检测列的变化
     */
    fun detectChanges(
        field: LsiField,
        dbColumn: JdbcColumnMetadata,
        config: DiffConfig = DiffConfig()
    ): Set<ColumnModification.ColumnChangeType> {
        val changes = mutableSetOf<ColumnModification.ColumnChangeType>()
        
        // 类型检查
        if (!isTypeMatched(field, dbColumn)) {
            changes.add(ColumnModification.ColumnChangeType.TYPE_CHANGED)
        }
        
        // 长度检查（仅对字符串类型）
        if (!config.strictTypeMatch && isStringType(field.getDatabaseColumnType())) {
            if (!isLengthMatched(field, dbColumn)) {
                changes.add(ColumnModification.ColumnChangeType.LENGTH_CHANGED)
            }
        }
        
        // 可空性检查
        if (!isNullableMatched(field, dbColumn)) {
            changes.add(ColumnModification.ColumnChangeType.NULLABLE_CHANGED)
        }
        
        // 注释检查
        if (config.compareComments && !isCommentMatched(field, dbColumn)) {
            changes.add(ColumnModification.ColumnChangeType.COMMENT_CHANGED)
        }
        
        return changes
    }
    
    /**
     * 类型匹配
     */
    private fun isTypeMatched(field: LsiField, dbColumn: JdbcColumnMetadata): Boolean {
        val lsiType = field.getDatabaseColumnType()
        val dbType = normalizeDbType(dbColumn.columnType ?: "")
        
        return when (lsiType) {
            DatabaseColumnType.INT -> dbType in setOf("int", "integer", "int4")
            DatabaseColumnType.BIGINT -> dbType in setOf("bigint", "int8", "long")
            DatabaseColumnType.SMALLINT -> dbType in setOf("smallint", "int2")
            DatabaseColumnType.TINYINT -> dbType in setOf("tinyint")
            DatabaseColumnType.DECIMAL -> dbType in setOf("decimal", "numeric")
            DatabaseColumnType.FLOAT -> dbType in setOf("float", "float4", "real")
            DatabaseColumnType.DOUBLE -> dbType in setOf("double", "float8", "double precision")
            DatabaseColumnType.VARCHAR -> dbType in setOf("varchar", "character varying", "text", "string")
            DatabaseColumnType.CHAR -> dbType in setOf("char", "character", "bpchar")
            DatabaseColumnType.TEXT -> dbType in setOf("text", "longtext", "clob")
            DatabaseColumnType.LONGTEXT -> dbType in setOf("longtext", "text", "clob")
            DatabaseColumnType.DATE -> dbType in setOf("date")
            DatabaseColumnType.TIME -> dbType in setOf("time")
            DatabaseColumnType.DATETIME -> dbType in setOf("datetime", "timestamp")
            DatabaseColumnType.TIMESTAMP -> dbType in setOf("timestamp", "datetime", "timestamptz")
            DatabaseColumnType.BOOLEAN -> dbType in setOf("boolean", "bool", "tinyint", "bit")
            DatabaseColumnType.BLOB -> dbType in setOf("blob", "bytea", "binary")
            DatabaseColumnType.BYTES -> dbType in setOf("bytes", "bytea", "binary", "varbinary")
        }
    }
    
    /**
     * 长度匹配
     */
    private fun isLengthMatched(field: LsiField, dbColumn: JdbcColumnMetadata): Boolean {
        val fieldLength = field.guessLength
        val dbLength = dbColumn.columnLength ?: return true
        
        // 如果 LSI 字段没有指定长度，使用默认值 255
        if (fieldLength <= 0) return true
        
        // 长度相同
        return fieldLength == dbLength
    }
    
    /**
     * 可空性匹配
     */
    private fun isNullableMatched(field: LsiField, dbColumn: JdbcColumnMetadata): Boolean {
        val fieldNullable = field.isNullable
        val dbNullable = !dbColumn.nullable  // dbColumn.nullable 表示 NOT NULL
        
        return fieldNullable == dbNullable
    }
    
    /**
     * 注释匹配
     */
    private fun isCommentMatched(field: LsiField, dbColumn: JdbcColumnMetadata): Boolean {
        val fieldComment = field.comment?.trim() ?: ""
        val dbComment = dbColumn.remarks?.trim() ?: ""
        
        return fieldComment == dbComment
    }
    
    /**
     * 判断是否为字符串类型
     */
    private fun isStringType(type: DatabaseColumnType): Boolean {
        return type in setOf(
            DatabaseColumnType.VARCHAR,
            DatabaseColumnType.CHAR,
            DatabaseColumnType.TEXT,
            DatabaseColumnType.LONGTEXT
        )
    }
    
    /**
     * 规范化数据库类型名称
     */
    private fun normalizeDbType(dbType: String): String {
        return dbType.lowercase()
            .replace("character varying", "varchar")
            .replace("character", "char")
            .replace("double precision", "double")
            .trim()
    }
}

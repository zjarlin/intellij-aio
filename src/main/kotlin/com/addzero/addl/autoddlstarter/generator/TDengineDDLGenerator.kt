package com.addzero.addl.autoddlstarter.generator

import com.addzero.addl.autoddlstarter.generator.entity.DDLContext
import com.addzero.addl.autoddlstarter.generator.entity.JavaFieldMetaInfo

/**
 * TDengine时序数据库DDL生成器
 * TDengine是一个专门为物联网、工业互联网等时序数据场景设计的数据库
 * 它使用超级表(STABLE)和子表(TABLE)的概念来组织数据
 */
class TDengineDDLGenerator : DatabaseDDLGenerator() {
    
    override fun generateCreateTableDDL(ddlContext: DDLContext): String {
        val tableName = ddlContext.tableEnglishName
        val tableComment = ddlContext.tableChineseName
        val fields = ddlContext.dto.filter { it.colName.isNotBlank() && it.colName != "ts" }
        
        val sqlBuilder = StringBuilder()
        
        // TDengine使用超级表(STABLE)概念来处理时序数据
        sqlBuilder.append("CREATE STABLE IF NOT EXISTS `${tableName}` (\n")
        
        // 添加时间戳字段，这是TDengine时序数据库的必需字段
        sqlBuilder.append("  `ts` TIMESTAMP")
        
        // 添加其他字段
        fields.forEach { field ->
            val fieldName = field.colName
            val fieldType = field.colType
            
            sqlBuilder.append(",\n  `${fieldName}` ${fieldType}")
        }
        
        sqlBuilder.append("\n) ")
        
        // 添加标签(TAGS)
        // TDengine使用标签(TAGS)来区分不同的数据源，例如设备ID等
        // 以下为示例标签，实际使用时请根据业务需求替换为真实的标签字段
        sqlBuilder.append("TAGS (\n")
        sqlBuilder.append("  `device_id` BINARY(64),\n")
        sqlBuilder.append("  `location` BINARY(64),\n")
        sqlBuilder.append("  `sensor_type` BINARY(64)\n")
        sqlBuilder.append(")")
        
        sqlBuilder.append(";\n")
        
        return sqlBuilder.toString()
    }

    override fun generateAddColDDL(ddlContext: DDLContext): String {
        val tableName = ddlContext.tableEnglishName
        val fields = ddlContext.dto
        
        val sqlBuilder = StringBuilder()
        
        fields.forEach { field ->
            val fieldName = field.colName
            val fieldType = field.colType
            
            // 跳过时间戳字段
            if (fieldName != "ts" && fieldName.isNotBlank()) {
                sqlBuilder.append("ALTER STABLE `${tableName}` ADD COLUMN `${fieldName}` ${fieldType}")
                sqlBuilder.append(";\n")
            }
        }
        
        return sqlBuilder.toString()
    }

    override fun mapTypeByMysqlType(mysqlType: String): String {
        return when (mysqlType.lowercase()) {
            "varchar", "char", "text", "mediumtext", "longtext" -> "NCHAR(255)"
            "int", "integer", "tinyint", "smallint", "mediumint" -> "INT"
            "bigint" -> "BIGINT"
            "float" -> "FLOAT"
            "double" -> "DOUBLE"
            "decimal", "numeric" -> "DOUBLE"
            "datetime", "timestamp" -> "TIMESTAMP"
            "date", "time" -> "TIMESTAMP"
            "boolean", "bool" -> "BOOL"
            else -> "NCHAR(255)"
        }
    }

    override fun mapTypeByJavaType(javaFieldMetaInfo: JavaFieldMetaInfo): String {
        val javaType = javaFieldMetaInfo.type.name.lowercase()
        
        return when {
            javaType.contains("string") -> "NCHAR(255)"
            javaType.contains("int") -> "INT"
            javaType.contains("long") -> "BIGINT"
            javaType.contains("float") -> "FLOAT"
            javaType.contains("double") -> "DOUBLE"
            javaType.contains("boolean") -> "BOOL"
            javaType.contains("date") || javaType.contains("time") || javaType.contains("localdatetime") -> "TIMESTAMP"
            javaType.contains("bigdecimal") -> "DOUBLE"
            else -> "NCHAR(255)"
        }
    }
}
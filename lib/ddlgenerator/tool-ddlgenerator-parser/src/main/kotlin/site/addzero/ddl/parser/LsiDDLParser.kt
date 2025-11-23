package site.addzero.ddl.parser

import site.addzero.ddl.core.model.ColumnDefinition
import site.addzero.ddl.core.model.TableDefinition
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.field.LsiField

/**
 * 基于LSI的DDL解析器
 *
 * 将LSI抽象的类信息解析为表定义
 */
class LsiDDLParser(
    private val fieldTypeMapper: FieldTypeMapper = FieldTypeMapper(),
    private val annotationExtractor: AnnotationExtractor = AnnotationExtractor()
) {

    /**
     * 解析LSI类为表定义
     *
     * @param lsiClass LSI类
     * @param databaseType 数据库类型
     * @return 表定义
     */
    fun parse(lsiClass: LsiClass, databaseType:  = "mysql"): TableDefinition {
        val tableInfo = annotationExtractor.extractTableInfo(lsiClass)
        val fields = lsiClass.fields

        val columns = fields
            .filter { shouldIncludeField(it) }
            .map { parseColumn(it) }

        val primaryKey = columns.find { it.primaryKey }?.name

        return TableDefinition(
            name = tableInfo.tableName,
            comment = tableInfo.tableComment,
            columns = columns,
            primaryKey = primaryKey,
            databaseName = tableInfo.databaseName
        )
    }

    /**
     * 解析字段为列定义
     */
    private fun parseColumn(field: LsiField): ColumnDefinition {
        val columnInfo = annotationExtractor.extractColumnInfo(field)
        val javaType = field.type?.qualifiedName ?: "java.lang.String"

        return ColumnDefinition(
            name = columnInfo.columnName,
            javaType = javaType,
            comment = columnInfo.comment,
            nullable = columnInfo.nullable,
            primaryKey = columnInfo.primaryKey,
            autoIncrement = columnInfo.autoIncrement,
            length = columnInfo.length,
            precision = columnInfo.precision,
            scale = columnInfo.scale
        )
    }

    /**
     * 判断是否应该包含该字段
     * 排除静态字段、transient字段等
     */
    private fun shouldIncludeField(field: LsiField): Boolean {
        // 使用 LSI 提供的 isDbField 属性
        return field.isDbField
    }
}


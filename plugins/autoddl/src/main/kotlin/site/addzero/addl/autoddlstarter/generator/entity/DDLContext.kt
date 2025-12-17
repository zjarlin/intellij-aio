package site.addzero.addl.autoddlstarter.generator.entity

/**
 * DDL 上下文信息
 */
data class DDLContext(
    val tableEnglishName: String,
    val tableComment: String?,
    val columns: List<ColumnMetaInfo>
)

/**
 * 列元信息
 */
data class ColumnMetaInfo(
    val columnName: String,
    val columnType: String,
    val isNullable: Boolean,
    val isPrimaryKey: Boolean,
    val defaultValue: String?,
    val columnComment: String?
)
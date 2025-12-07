package site.addzero.lsi.analyzer.ddl

/**
 * DDL 操作类型
 */
enum class DdlOperationType(val templateName: String) {
    CREATE_TABLE("create-table"),
    ALTER_ADD_COLUMN("alter-add-column"),
    ALTER_MODIFY_COLUMN("alter-modify-column"),
    DROP_TABLE("drop-table"),
    CREATE_INDEX("create-index"),
    DROP_INDEX("drop-index"),
    ADD_FOREIGN_KEY("add-foreign-key"),
    ADD_COMMENT("add-comment");
}

package site.addzero.addl.autoddlstarter.generator


abstract class DatabaseDDLGenerator : site.addzero.addl.autoddlstarter.generator.IDatabaseGenerator {
    /**建表语句
     * @param [ddlContext]
     * @return [String]
     */
    abstract fun generateCreateTableDDL(ddlContext: site.addzero.addl.autoddlstarter.generator.entity.DDLContext): String

    /**
     * 加列语句
     * @param [ddlContext]
     * @return [String]
     */
    abstract fun generateAddColDDL(ddlContext: site.addzero.addl.autoddlstarter.generator.entity.DDLContext): String



//    abstract fun printChangeDML(dmlContext: DMLContext): String
}

package site.addzero.lsi.generator.ddl

import site.addzero.lsi.analyzer.metadata.LsiClass
import site.addzero.lsi.generator.contract.BatchCodeGenerator

class DDLCodeGenerator private constructor(
    private val dialect: String
) : BatchCodeGenerator<LsiClass, DDLResult> {

    private val generator: SqlDDLGenerator by lazy {
        SqlDDLGenerator.forDatabase(dialect)
    }

    companion object {
        fun forDialect(dialect: String): DDLCodeGenerator = DDLCodeGenerator(dialect)

        fun forJdbcUrl(jdbcUrl: String): DDLCodeGenerator {
            val dialect = inferDialectFromUrl(jdbcUrl)
            return DDLCodeGenerator(dialect)
        }

        fun mysql(): DDLCodeGenerator = DDLCodeGenerator("mysql")
        fun postgresql(): DDLCodeGenerator = DDLCodeGenerator("postgresql")
        fun oracle(): DDLCodeGenerator = DDLCodeGenerator("oracle")
        fun h2(): DDLCodeGenerator = DDLCodeGenerator("h2")

        private fun inferDialectFromUrl(jdbcUrl: String): String {
            val lowerUrl = jdbcUrl.lowercase()
            return when {
                lowerUrl.contains("mysql") || lowerUrl.contains("mariadb") -> "mysql"
                lowerUrl.contains("postgresql") || lowerUrl.contains("postgres") -> "postgresql"
                lowerUrl.contains("oracle") -> "oracle"
                lowerUrl.contains("h2") -> "h2"
                lowerUrl.contains("dm") || lowerUrl.contains("dameng") -> "dm"
                lowerUrl.contains("sqlserver") || lowerUrl.contains("mssql") -> "sqlserver"
                lowerUrl.contains("taos") || lowerUrl.contains("tdengine") -> "tdengine"
                else -> "mysql"
            }
        }
    }

    override fun support(input: LsiClass): Boolean {
        return input.dbFields.isNotEmpty()
    }

    override fun generate(input: LsiClass): DDLResult {
        val tableDefinition = input.toTableDefinition()

        val createTable = generator.generateCreateTable(tableDefinition)
        val alterTable = generator.generateAlterTableAddColumn(tableDefinition)

        return DDLResult(
            tableName = tableDefinition.name,
            className = input.className,
            createTableDDL = createTable,
            alterTableDDLs = alterTable,
            fullDDL = buildString {
                appendLine(createTable)
                appendLine()
                alterTable.forEach { appendLine(it) }
            }
        )
    }
}

data class DDLResult(
    val tableName: String,
    val className: String,
    val createTableDDL: String,
    val alterTableDDLs: List<String>,
    val fullDDL: String
)

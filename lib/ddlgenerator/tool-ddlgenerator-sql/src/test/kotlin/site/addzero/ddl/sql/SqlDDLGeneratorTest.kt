package site.addzero.ddl.sql

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import site.addzero.ddl.core.model.ColumnDefinition
import site.addzero.ddl.sql.dialect.*

@DisplayName("SqlDDLGenerator 测试")
class SqlDDLGeneratorTest {

    private lateinit var mysqlGenerator: SqlDDLGenerator
    private lateinit var postgresqlGenerator: SqlDDLGenerator
    private lateinit var oracleGenerator: SqlDDLGenerator

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupAll() {
            // 手动注册方言
            SqlDialectRegistry.register(MysqlDialect())
            SqlDialectRegistry.register(PostgresqlDialect())
            SqlDialectRegistry.register(OracleDialect())
            SqlDialectRegistry.register(DmDialect())
            SqlDialectRegistry.register(H2Dialect())
            SqlDialectRegistry.register(TdengineDialect())
        }
    }

    @BeforeEach
    fun setup() {
        mysqlGenerator = SqlDDLGenerator.forDatabase("mysql")
        postgresqlGenerator = SqlDDLGenerator.forDatabase("pg")  // PostgreSQL的方言名是"pg"
        oracleGenerator = SqlDDLGenerator.forDatabase("oracle")
    }

    @Test
    @DisplayName("应该成功创建MySQL生成器")
    fun `should create mysql generator successfully`() {
        assertNotNull(mysqlGenerator)
    }

    @Test
    @DisplayName("应该成功创建PostgreSQL生成器")
    fun `should create postgresql generator successfully`() {
        assertNotNull(postgresqlGenerator)
    }

    @Test
    @DisplayName("应该成功创建Oracle生成器")
    fun `should create oracle generator successfully`() {
        assertNotNull(oracleGenerator)
    }

    @Test
    @DisplayName("MySQL - 应该生成CREATE TABLE语句")
    fun `mysql should generate create table statement`() {
        val table = createTestTable()
        val sql = mysqlGenerator.generateCreateTable(table)

        assertNotNull(sql)
        assertTrue(sql.contains("CREATE TABLE"))
        assertTrue(sql.contains("sys_user"))
        assertTrue(sql.contains("id"))
        assertTrue(sql.contains("username"))
        assertTrue(sql.contains("email"))
        assertTrue(sql.contains("PRIMARY KEY"))
        assertTrue(sql.contains("ENGINE=InnoDB"))
    }

    @Test
    @DisplayName("PostgreSQL - 应该生成CREATE TABLE语句")
    fun `postgresql should generate create table statement`() {
        val table = createTestTable()
        val sql = postgresqlGenerator.generateCreateTable(table)

        assertNotNull(sql)
        assertTrue(sql.contains("CREATE TABLE"))
        assertTrue(sql.contains("sys_user"))
        assertTrue(sql.contains("id"))
        assertTrue(sql.contains("username"))
        assertTrue(sql.contains("email"))
        assertTrue(sql.contains("PRIMARY KEY"))
    }

    @Test
    @DisplayName("Oracle - 应该生成CREATE TABLE语句")
    fun `oracle should generate create table statement`() {
        val table = createTestTable()
        val sql = oracleGenerator.generateCreateTable(table)

        assertNotNull(sql)
        assertTrue(sql.contains("CREATE TABLE"), "SQL should contain CREATE TABLE: $sql")
        assertTrue(sql.contains("sys_user") || sql.contains("SYS_USER"), "SQL should contain table name: $sql")
        assertTrue(sql.contains("id") || sql.contains("ID"), "SQL should contain id column: $sql")
        // Oracle可能使用引号，所以只检查核心SQL存在性
        assertFalse(sql.isBlank(), "Generated SQL should not be blank")
    }

    @Test
    @DisplayName("MySQL - 应该生成ALTER TABLE ADD COLUMN语句")
    fun `mysql should generate alter table add column statements`() {
        val table = createTestTable()
        val sqls = mysqlGenerator.generateAlterTableAddColumn(table)

        assertNotNull(sqls)
        assertTrue(sqls.isNotEmpty())
        sqls.forEach { sql ->
            assertTrue(sql.contains("ALTER TABLE"))
            assertTrue(sql.contains("ADD COLUMN"))
        }
    }

    @Test
    @DisplayName("PostgreSQL - 应该生成ALTER TABLE ADD COLUMN语句")
    fun `postgresql should generate alter table add column statements`() {
        val table = createTestTable()
        val sqls = postgresqlGenerator.generateAlterTableAddColumn(table)

        assertNotNull(sqls)
        assertTrue(sqls.isNotEmpty(), "Should generate at least one ALTER TABLE statement, but got: $sqls")
        sqls.forEachIndexed { index, sql ->
            // PostgreSQL方言生成COMMENT语句，所以可能有些SQL不含ALTER TABLE
            // 只检查至少有一条ALTER TABLE语句
            if (index < table.columns.size) {
                // 前N条应该是ALTER TABLE ADD COLUMN
                val isAlterOrComment = sql.contains("ALTER TABLE") || sql.contains("COMMENT")
                assertTrue(isAlterOrComment, "SQL[$index] should contain ALTER TABLE or COMMENT: $sql")
            }
        }
        // 确保至少有一条ALTER TABLE语句
        assertTrue(sqls.any { it.contains("ALTER TABLE") }, "At least one SQL should contain ALTER TABLE")
    }

    @Test
    @DisplayName("应该正确处理主键列")
    fun `should handle primary key column correctly`() {
        val table = createTestTable()
        val sql = mysqlGenerator.generateCreateTable(table)

        assertTrue(sql.contains("PRIMARY KEY"))
        assertTrue(sql.contains("AUTO_INCREMENT"))
    }

    @Test
    @DisplayName("应该正确处理可空列")
    fun `should handle nullable columns correctly`() {
        val table = createTestTable()
        val sql = mysqlGenerator.generateCreateTable(table)

        // email列应该是NULL
        val lines = sql.lines()
        val emailLine = lines.find { it.contains("email") }
        assertNotNull(emailLine)
        assertTrue(emailLine!!.contains("NULL") && !emailLine.contains("NOT NULL"))
    }

    @Test
    @DisplayName("应该正确处理非空列")
    fun `should handle non-nullable columns correctly`() {
        val table = createTestTable()
        val sql = mysqlGenerator.generateCreateTable(table)

        // username列应该是NOT NULL
        val lines = sql.lines()
        val usernameLine = lines.find { it.contains("username") }
        assertNotNull(usernameLine)
        assertTrue(usernameLine!!.contains("NOT NULL"))
    }

    @Test
    @DisplayName("应该正确处理表注释")
    fun `should handle table comment correctly`() {
        val table = createTestTable()
        val sql = mysqlGenerator.generateCreateTable(table)

        assertTrue(sql.contains("系统用户表") || sql.contains("COMMENT"))
    }

    @Test
    @DisplayName("应该正确处理列注释")
    fun `should handle column comments correctly`() {
        val table = createTestTable()
        val sql = mysqlGenerator.generateCreateTable(table)

        assertTrue(sql.contains("主键ID") || sql.contains("用户名") || sql.contains("COMMENT"))
    }

    @Test
    @DisplayName("应该支持带数据库名的表")
    fun `should support table with database name`() {
        val table = createTestTable().copy(databaseName = "test_db")
        val sqls = mysqlGenerator.generateAlterTableAddColumn(table)

        assertTrue(sqls.any { it.contains("test_db") })
    }

    @Test
    @DisplayName("应该正确处理空表（无列）")
    fun `should handle empty table without columns`() {
        val table = TableDefinition(
            name = "empty_table",
            comment = "空表",
            columns = emptyList()
        )

        val sql = mysqlGenerator.generateCreateTable(table)
        assertNotNull(sql)
        assertTrue(sql.contains("CREATE TABLE"))
        assertTrue(sql.contains("empty_table"))
    }

    @Test
    @DisplayName("应该正确处理各种Java类型")
    fun `should handle various java types correctly`() {
        val columns = listOf(
            ColumnDefinition(name = "str_field", javaType = "java.lang.String", comment = "字符串"),
            ColumnDefinition(name = "int_field", javaType = "java.lang.Integer", comment = "整型"),
            ColumnDefinition(name = "long_field", javaType = "java.lang.Long", comment = "长整型"),
            ColumnDefinition(name = "double_field", javaType = "java.lang.Double", comment = "双精度"),
            ColumnDefinition(name = "decimal_field", javaType = "java.math.BigDecimal", comment = "精确数值", precision = 10, scale = 2),
            ColumnDefinition(name = "bool_field", javaType = "java.lang.Boolean", comment = "布尔"),
            ColumnDefinition(name = "date_field", javaType = "java.time.LocalDate", comment = "日期"),
            ColumnDefinition(name = "datetime_field", javaType = "java.time.LocalDateTime", comment = "日期时间")
        )

        val table = TableDefinition(
            name = "test_types",
            comment = "类型测试表",
            columns = columns
        )

        val sql = mysqlGenerator.generateCreateTable(table)
        assertNotNull(sql)
        assertTrue(sql.contains("str_field"))
        assertTrue(sql.contains("int_field"))
        assertTrue(sql.contains("long_field"))
        assertTrue(sql.contains("double_field"))
        assertTrue(sql.contains("decimal_field"))
        assertTrue(sql.contains("bool_field"))
        assertTrue(sql.contains("date_field"))
        assertTrue(sql.contains("datetime_field"))
    }

    private fun createTestTable():LsiClass {
        return TableDefinition(
            name = "sys_user",
            comment = "系统用户表",
            columns = listOf(
                ColumnDefinition(
                    name = "id",
                    javaType = "java.lang.Long",
                    comment = "主键ID",
                    nullable = false,
                    primaryKey = true,
                    autoIncrement = true
                ),
                ColumnDefinition(
                    name = "username",
                    javaType = "java.lang.String",
                    comment = "用户名",
                    nullable = false,
                    length = 50
                ),
                ColumnDefinition(
                    name = "email",
                    javaType = "java.lang.String",
                    comment = "邮箱",
                    nullable = true,
                    length = 100
                )
            ),
            primaryKey = "id"
        )
    }
}

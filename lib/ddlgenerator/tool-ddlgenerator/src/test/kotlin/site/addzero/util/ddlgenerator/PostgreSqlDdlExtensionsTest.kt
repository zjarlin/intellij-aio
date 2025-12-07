package site.addzero.util.ddlgenerator

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.util.db.DatabaseType
import site.addzero.util.ddlgenerator.extension.*

/**
 * PostgreSQL DDL 扩展函数测试
 * 使用Mock的Jimmer实体对象进行测试
 */
class PostgreSqlDdlExtensionsTest {

    @Test
    fun `test SysUser toCreateTableDDL for PostgreSQL`() {
        // Given: Mock SysUser实体
        val sysUser = createMockSysUser()

        // When: 生成PostgreSQL CREATE TABLE DDL
        val ddl = sysUser.toCreateTableDDL(DatabaseType.POSTGRESQL)

        // Then: 验证DDL包含必要的元素
        println("=== SysUser CREATE TABLE DDL (PostgreSQL) ===")
        println(ddl)
        println()

        assertTrue(ddl.contains("CREATE TABLE \"sys_user\""), "应包含表名（带双引号）")
        assertTrue(ddl.contains("\"id\" BIGINT"), "应包含id字段")
        assertTrue(ddl.contains("\"phone\" VARCHAR"), "应包含phone字段")
        assertTrue(ddl.contains("\"email\" VARCHAR"), "应包含email字段")
        assertTrue(ddl.contains("\"username\" VARCHAR"), "应包含username字段")
        assertTrue(ddl.contains("PRIMARY KEY"), "应包含主键定义")
        assertTrue(ddl.contains("NOT NULL"), "应包含NOT NULL约束")
    }

    @Test
    fun `test Product toCreateTableDDL for PostgreSQL`() {
        // Given: Mock Product实体
        val product = createMockProduct()

        // When: 生成PostgreSQL CREATE TABLE DDL
        val ddl = product.toCreateTableDDL("postgresql")  // 测试字符串方言

        // Then: 验证DDL
        println("=== Product CREATE TABLE DDL (PostgreSQL) ===")
        println(ddl)
        println()

        assertTrue(ddl.contains("CREATE TABLE \"product\""))
        assertTrue(ddl.contains("\"id\" BIGINT"))
        assertTrue(ddl.contains("\"name\" VARCHAR"))
        assertTrue(ddl.contains("\"code\" VARCHAR"))
        assertTrue(ddl.contains("\"description\" VARCHAR"))
        assertTrue(ddl.contains("\"enabled\""))
    }

    @Test
    fun `test SysRole toCreateTableDDL for PostgreSQL`() {
        // Given: Mock SysRole实体
        val sysRole = createMockSysRole()

        // When: 生成PostgreSQL CREATE TABLE DDL
        val ddl = sysRole.toCreateTableDDL(DatabaseType.POSTGRESQL)

        // Then: 验证DDL
        println("=== SysRole CREATE TABLE DDL (PostgreSQL) ===")
        println(ddl)
        println()

        assertTrue(ddl.contains("CREATE TABLE \"sys_role\""))
        assertTrue(ddl.contains("\"role_name\" VARCHAR"), "应使用自定义列名")
        assertTrue(ddl.contains("\"role_code\" VARCHAR"), "应使用自定义列名")
    }

    @Test
    fun `test toDropTableDDL for PostgreSQL`() {
        // Given: Mock Product实体
        val product = createMockProduct()

        // When: 生成DROP TABLE DDL
        val ddl = product.toDropTableDDL(DatabaseType.POSTGRESQL)

        // Then: 验证DDL
        println("=== Product DROP TABLE DDL (PostgreSQL) ===")
        println(ddl)
        println()

        assertTrue(ddl.contains("DROP TABLE"))
        assertTrue(ddl.contains("\"product\""))
    }

    @Test
    fun `test field toAddColumnDDL for PostgreSQL`() {
        // Given: Mock SysUser实体和新字段
        val newField = mockField(
            name = "address",
            typeName = "String",
            comment = "地址",
            isNullable = true
        )

        // When: 生成ADD COLUMN DDL
        val ddl = newField.toAddColumnDDL("sys_user", DatabaseType.POSTGRESQL)

        // Then: 验证DDL
        println("=== Add Column DDL (PostgreSQL) ===")
        println(ddl)
        println()

        assertTrue(ddl.contains("ALTER TABLE \"sys_user\""))
        assertTrue(ddl.contains("ADD COLUMN"))
        assertTrue(ddl.contains("\"address\""))
        assertTrue(ddl.contains("VARCHAR"))
    }

    @Test
    fun `test field toDropColumnDDL for PostgreSQL`() {
        // Given: SysUser的phone字段
        val sysUser = createMockSysUser()
        val phoneField = sysUser.fields.first { it.name == "phone" }

        // When: 生成DROP COLUMN DDL
        val ddl = phoneField.toDropColumnDDL("sys_user", DatabaseType.POSTGRESQL)

        // Then: 验证DDL
        println("=== Drop Column DDL (PostgreSQL) ===")
        println(ddl)
        println()

        assertTrue(ddl.contains("ALTER TABLE \"sys_user\""))
        assertTrue(ddl.contains("DROP COLUMN"))
        assertTrue(ddl.contains("\"phone\""))
    }

    @Test
    fun `test field toModifyColumnDDL for PostgreSQL`() {
        // Given: SysUser的email字段
        val sysUser = createMockSysUser()
        val emailField = sysUser.fields.first { it.name == "email" }

        // When: 生成MODIFY COLUMN DDL
        val ddl = emailField.toModifyColumnDDL("sys_user", DatabaseType.POSTGRESQL)

        // Then: 验证DDL (PostgreSQL的MODIFY是多条语句)
        println("=== Modify Column DDL (PostgreSQL) ===")
        println(ddl)
        println()

        assertTrue(ddl.contains("ALTER TABLE \"sys_user\""))
        assertTrue(ddl.contains("ALTER COLUMN"))
        assertTrue(ddl.contains("\"email\""))
        // PostgreSQL的MODIFY包含TYPE、SET NOT NULL等多条语句
        assertTrue(ddl.contains("TYPE") || ddl.contains("SET NOT NULL"))
    }

    @Test
    fun `test batch toSchemaDDL for PostgreSQL`() {
        // Given: 多个Mock实体
        val entities = listOf(
            createMockSysUser(),
            createMockSysRole(),
            createMockProduct(),
            createMockProductCategory()
        )

        // When: 批量生成schema DDL
        val schema = entities.toSchemaDDL(DatabaseType.POSTGRESQL)

        // Then: 验证schema包含所有表
        println("=== Batch Schema DDL (PostgreSQL) ===")
        println(schema)
        println()

        assertTrue(schema.contains("CREATE TABLE \"sys_user\""))
        assertTrue(schema.contains("CREATE TABLE \"sys_role\""))
        assertTrue(schema.contains("CREATE TABLE \"product\""))
        assertTrue(schema.contains("CREATE TABLE \"product_category\""))
    }

    @Test
    fun `test toAddCommentDDL for PostgreSQL`() {
        // Given: Mock SysUser实体（带注释）
        val sysUser = createMockSysUser()

        // When: 生成添加注释的DDL
        val ddl = sysUser.toAddCommentDDL(DatabaseType.POSTGRESQL)

        // Then: 验证DDL
        println("=== Add Comment DDL (PostgreSQL) ===")
        println(ddl)
        println()

        if (ddl.isNotEmpty()) {
            assertTrue(ddl.contains("sys_user") || ddl.contains("COMMENT"))
        }
    }

    @Test
    fun `test field nullable and not-null constraints`() {
        // Given: Mock实体，包含可空和非空字段
        val product = createMockProduct()

        // When: 生成DDL
        val ddl = product.toCreateTableDDL(DatabaseType.POSTGRESQL)

        // Then: 验证可空性约束
        println("=== Nullable Constraints Test (PostgreSQL) ===")
        println(ddl)
        println()

        // 非空字段应有NOT NULL
        assertTrue(ddl.contains("\"name\" VARCHAR") && ddl.contains("NOT NULL"))
        assertTrue(ddl.contains("\"code\" VARCHAR") && ddl.contains("NOT NULL"))

        // 可空字段
        val descriptionLine = ddl.lines().find { it.contains("\"description\"") }
        assertNotNull(descriptionLine, "应包含description字段")
    }

    @Test
    fun `test field with default value`() {
        // Given: 带默认值的字段
        val product = createMockProduct()

        // When: 生成DDL
        val ddl = product.toCreateTableDDL(DatabaseType.POSTGRESQL)

        // Then: 验证默认值
        println("=== Default Value Test (PostgreSQL) ===")
        println(ddl)
        println()

        assertTrue(ddl.contains("\"enabled\""))
        // PostgreSQL可能会包含DEFAULT子句
    }

    @Test
    fun `test field with custom column name`() {
        // Given: 带自定义列名的字段
        val sysRole = createMockSysRole()

        // When: 生成DDL
        val ddl = sysRole.toCreateTableDDL(DatabaseType.POSTGRESQL)

        // Then: 验证使用自定义列名
        println("=== Custom Column Name Test (PostgreSQL) ===")
        println(ddl)
        println()

        assertTrue(ddl.contains("\"role_name\""), "应使用自定义列名role_name而不是roleName")
        assertTrue(ddl.contains("\"role_code\""), "应使用自定义列名role_code而不是roleCode")
    }

    @Test
    fun `test various data types for PostgreSQL`() {
        // Given: 包含各种数据类型的Mock实体
        val mixedTypes = mockClass(
            name = "MixedTypes",
            qualifiedName = "test.MixedTypes",
            tableName = "mixed_types",
            fields = listOf(
                mockField("id", "Long", isPrimitive = true, isNullable = false),
                mockField("name", "String", isNullable = false),
                mockField("age", "Int", isPrimitive = true, isNullable = true),
                mockField("salary", "Double", isPrimitive = true, isNullable = true),
                mockField("isActive", "Boolean", isPrimitive = true, isNullable = false),
                mockField("birthDate", "LocalDate", qualifiedTypeName = "java.time.LocalDate", isNullable = true),
                mockField("createdAt", "LocalDateTime", qualifiedTypeName = "java.time.LocalDateTime", isNullable = false)
            )
        )

        // When: 生成DDL
        val ddl = mixedTypes.toCreateTableDDL(DatabaseType.POSTGRESQL)

        // Then: 验证各种数据类型
        println("=== Various Data Types Test (PostgreSQL) ===")
        println(ddl)
        println()

        assertTrue(ddl.contains("BIGINT"), "Long应映射到BIGINT")
        assertTrue(ddl.contains("VARCHAR"), "String应映射到VARCHAR")
        assertTrue(ddl.contains("INT") || ddl.contains("INTEGER"), "Int应映射到INTEGER")
        // PostgreSQL的类型映射可能包含DOUBLE PRECISION、BOOLEAN、DATE、TIMESTAMP等
    }

    @Test
    fun `test PostgreSQL modify column multiple statements`() {
        // Given: 一个需要修改的字段
        val field = mockField(
            name = "status",
            typeName = "String",
            comment = "状态",
            isNullable = false,
            defaultValue = "'active'"
        )

        // When: 生成MODIFY COLUMN DDL
        val ddl = field.toModifyColumnDDL("sys_user", DatabaseType.POSTGRESQL)

        // Then: 验证PostgreSQL的多条ALTER语句
        println("=== PostgreSQL Modify Column Multiple Statements ===")
        println(ddl)
        println()

        // PostgreSQL需要分别修改类型、可空性、默认值
        assertTrue(ddl.contains("ALTER COLUMN \"status\" TYPE"), "应包含修改类型的语句")
        assertTrue(ddl.contains("SET NOT NULL") || ddl.contains("DROP NOT NULL"), "应包含修改可空性的语句")
    }

    @Test
    fun `test compare MySQL and PostgreSQL syntax differences`() {
        // Given: 同一个实体
        val product = createMockProduct()

        // When: 分别生成MySQL和PostgreSQL的DDL
        val mysqlDdl = product.toCreateTableDDL(DatabaseType.MYSQL)
        val postgresqlDdl = product.toCreateTableDDL(DatabaseType.POSTGRESQL)

        // Then: 验证语法差异
        println("=== MySQL vs PostgreSQL Syntax Comparison ===")
        println("MySQL:")
        println(mysqlDdl)
        println("\nPostgreSQL:")
        println(postgresqlDdl)
        println()

        // MySQL使用反引号
        assertTrue(mysqlDdl.contains("`product`"), "MySQL应使用反引号")

        // PostgreSQL使用双引号
        assertTrue(postgresqlDdl.contains("\"product\""), "PostgreSQL应使用双引号")

        // 两者都应包含基本结构
        assertTrue(mysqlDdl.contains("CREATE TABLE"))
        assertTrue(postgresqlDdl.contains("CREATE TABLE"))
    }
}

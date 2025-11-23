package site.addzero.ddl.core.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName

@DisplayName("TableDefinition 测试")
class TableDefinitionTest {

    @Test
    @DisplayName("应该正确创建表定义")
    fun `should create table definition correctly`() {
        val columns = listOf(
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
        )

        val tableDef = TableDefinition(
            name = "sys_user",
            comment = "系统用户表",
            columns = columns,
            primaryKey = "id",
            databaseName = "test_db"
        )

        assertEquals("sys_user", tableDef.name)
        assertEquals("系统用户表", tableDef.comment)
        assertEquals(3, tableDef.columns.size)
        assertEquals("id", tableDef.primaryKey)
        assertEquals("test_db", tableDef.databaseName)
    }

    @Test
    @DisplayName("应该正确获取非主键列")
    fun `should get non-primary columns correctly`() {
        val columns = listOf(
            ColumnDefinition(name = "id", javaType = "java.lang.Long", comment = "ID", primaryKey = true),
            ColumnDefinition(name = "name", javaType = "java.lang.String", comment = "名称"),
            ColumnDefinition(name = "age", javaType = "java.lang.Integer", comment = "年龄")
        )

        val tableDef = TableDefinition(
            name = "test_table",
            comment = "测试表",
            columns = columns
        )

        val nonPrimaryColumns = tableDef.nonPrimaryColumns
        assertEquals(2, nonPrimaryColumns.size)
        assertTrue(nonPrimaryColumns.all { !it.primaryKey })
        assertEquals(listOf("name", "age"), nonPrimaryColumns.map { it.name })
    }

    @Test
    @DisplayName("应该正确获取主键列")
    fun `should get primary key column correctly`() {
        val idColumn = ColumnDefinition(
            name = "id",
            javaType = "java.lang.Long",
            comment = "ID",
            primaryKey = true,
            autoIncrement = true
        )
        
        val columns = listOf(
            idColumn,
            ColumnDefinition(name = "name", javaType = "java.lang.String", comment = "名称")
        )

        val tableDef = TableDefinition(
            name = "test_table",
            comment = "测试表",
            columns = columns,
            primaryKey = "id"
        )

        val primaryKeyColumn = tableDef.primaryKeyColumn
        assertNotNull(primaryKeyColumn)
        assertEquals("id", primaryKeyColumn?.name)
        assertTrue(primaryKeyColumn?.primaryKey ?: false)
        assertTrue(primaryKeyColumn?.autoIncrement ?: false)
    }

    @Test
    @DisplayName("当没有主键时应该返回null")
    fun `should return null when no primary key exists`() {
        val columns = listOf(
            ColumnDefinition(name = "name", javaType = "java.lang.String", comment = "名称"),
            ColumnDefinition(name = "age", javaType = "java.lang.Integer", comment = "年龄")
        )

        val tableDef = TableDefinition(
            name = "test_table",
            comment = "测试表",
            columns = columns
        )

        assertNull(tableDef.primaryKeyColumn)
        assertEquals(columns, tableDef.nonPrimaryColumns)
    }

    @Test
    @DisplayName("数据类的相等性应该正常工作")
    fun `data class equality should work correctly`() {
        val columns = listOf(
            ColumnDefinition(name = "id", javaType = "java.lang.Long", comment = "ID", primaryKey = true)
        )

        val table1 = TableDefinition(
            name = "test_table",
            comment = "测试表",
            columns = columns
        )

        val table2 = TableDefinition(
            name = "test_table",
            comment = "测试表",
            columns = columns
        )

        val table3 = TableDefinition(
            name = "other_table",
            comment = "其他表",
            columns = columns
        )

        assertEquals(table1, table2)
        assertNotEquals(table1, table3)
        assertEquals(table1.hashCode(), table2.hashCode())
    }

    @Test
    @DisplayName("应该支持空列表")
    fun `should support empty columns list`() {
        val tableDef = TableDefinition(
            name = "empty_table",
            comment = "空表",
            columns = emptyList()
        )

        assertTrue(tableDef.columns.isEmpty())
        assertTrue(tableDef.nonPrimaryColumns.isEmpty())
        assertNull(tableDef.primaryKeyColumn)
    }

    @Test
    @DisplayName("默认数据库名应该为空字符串")
    fun `default database name should be empty string`() {
        val tableDef = TableDefinition(
            name = "test_table",
            comment = "测试表",
            columns = emptyList()
        )

        assertEquals("", tableDef.databaseName)
    }
}

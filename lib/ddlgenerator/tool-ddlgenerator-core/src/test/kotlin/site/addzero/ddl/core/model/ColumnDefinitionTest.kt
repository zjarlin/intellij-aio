package site.addzero.ddl.core.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName

@DisplayName("ColumnDefinition 测试")
class ColumnDefinitionTest {

    @Test
    @DisplayName("应该正确创建列定义")
    fun `should create column definition correctly`() {
        val column = ColumnDefinition(
            name = "username",
            javaType = "java.lang.String",
            comment = "用户名",
            nullable = false,
            primaryKey = false,
            autoIncrement = false,
            length = 50,
            precision = -1,
            scale = -1
        )

        assertEquals("username", column.name)
        assertEquals("java.lang.String", column.javaType)
        assertEquals("用户名", column.comment)
        assertFalse(column.nullable)
        assertFalse(column.primaryKey)
        assertFalse(column.autoIncrement)
        assertEquals(50, column.length)
        assertEquals(-1, column.precision)
        assertEquals(-1, column.scale)
    }

    @Test
    @DisplayName("应该正确提取简单Java类型名")
    fun `should extract simple java type name correctly`() {
        val stringColumn = ColumnDefinition(
            name = "name",
            javaType = "java.lang.String",
            comment = "名称"
        )
        assertEquals("String", stringColumn.simpleJavaType)

        val longColumn = ColumnDefinition(
            name = "id",
            javaType = "java.lang.Long",
            comment = "ID"
        )
        assertEquals("Long", longColumn.simpleJavaType)

        val intColumn = ColumnDefinition(
            name = "count",
            javaType = "java.lang.Integer",
            comment = "数量"
        )
        assertEquals("Integer", intColumn.simpleJavaType)

        val dateColumn = ColumnDefinition(
            name = "created_at",
            javaType = "java.util.Date",
            comment = "创建时间"
        )
        assertEquals("Date", dateColumn.simpleJavaType)
    }

    @Test
    @DisplayName("应该正确处理无包名的类型")
    fun `should handle types without package correctly`() {
        val column = ColumnDefinition(
            name = "value",
            javaType = "String",
            comment = "值"
        )
        assertEquals("String", column.simpleJavaType)
    }

    @Test
    @DisplayName("默认值应该正确")
    fun `default values should be correct`() {
        val column = ColumnDefinition(
            name = "field",
            javaType = "java.lang.String",
            comment = "字段"
        )

        assertTrue(column.nullable)
        assertFalse(column.primaryKey)
        assertFalse(column.autoIncrement)
        assertEquals(-1, column.length)
        assertEquals(-1, column.precision)
        assertEquals(-1, column.scale)
    }

    @Test
    @DisplayName("应该正确创建主键列")
    fun `should create primary key column correctly`() {
        val primaryKeyColumn = ColumnDefinition(
            name = "id",
            javaType = "java.lang.Long",
            comment = "主键ID",
            nullable = false,
            primaryKey = true,
            autoIncrement = true
        )

        assertTrue(primaryKeyColumn.primaryKey)
        assertTrue(primaryKeyColumn.autoIncrement)
        assertFalse(primaryKeyColumn.nullable)
    }

    @Test
    @DisplayName("应该正确创建VARCHAR列")
    fun `should create varchar column correctly`() {
        val varcharColumn = ColumnDefinition(
            name = "description",
            javaType = "java.lang.String",
            comment = "描述",
            nullable = true,
            length = 255
        )

        assertEquals(255, varcharColumn.length)
        assertTrue(varcharColumn.nullable)
        assertEquals(-1, varcharColumn.precision)
        assertEquals(-1, varcharColumn.scale)
    }

    @Test
    @DisplayName("应该正确创建DECIMAL列")
    fun `should create decimal column correctly`() {
        val decimalColumn = ColumnDefinition(
            name = "price",
            javaType = "java.math.BigDecimal",
            comment = "价格",
            nullable = false,
            precision = 10,
            scale = 2
        )

        assertEquals(10, decimalColumn.precision)
        assertEquals(2, decimalColumn.scale)
        assertEquals(-1, decimalColumn.length)
        assertFalse(decimalColumn.nullable)
    }

    @Test
    @DisplayName("数据类的相等性应该正常工作")
    fun `data class equality should work correctly`() {
        val column1 = ColumnDefinition(
            name = "username",
            javaType = "java.lang.String",
            comment = "用户名",
            length = 50
        )

        val column2 = ColumnDefinition(
            name = "username",
            javaType = "java.lang.String",
            comment = "用户名",
            length = 50
        )

        val column3 = ColumnDefinition(
            name = "email",
            javaType = "java.lang.String",
            comment = "邮箱",
            length = 100
        )

        assertEquals(column1, column2)
        assertNotEquals(column1, column3)
        assertEquals(column1.hashCode(), column2.hashCode())
    }

    @Test
    @DisplayName("copy方法应该正常工作")
    fun `copy method should work correctly`() {
        val original = ColumnDefinition(
            name = "username",
            javaType = "java.lang.String",
            comment = "用户名",
            nullable = false,
            length = 50
        )

        val copied = original.copy(comment = "用户登录名", length = 100)

        assertEquals("username", copied.name)
        assertEquals("java.lang.String", copied.javaType)
        assertEquals("用户登录名", copied.comment)
        assertFalse(copied.nullable)
        assertEquals(100, copied.length)
    }

    @Test
    @DisplayName("应该支持各种Java类型")
    fun `should support various java types`() {
        val types = listOf(
            "java.lang.String",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Double",
            "java.lang.Float",
            "java.lang.Boolean",
            "java.math.BigDecimal",
            "java.util.Date",
            "java.time.LocalDateTime",
            "java.time.LocalDate",
            "java.time.LocalTime"
        )

        types.forEach { javaType ->
            val column = ColumnDefinition(
                name = "test_field",
                javaType = javaType,
                comment = "测试字段"
            )
            assertEquals(javaType, column.javaType)
            assertNotNull(column.simpleJavaType)
        }
    }
}

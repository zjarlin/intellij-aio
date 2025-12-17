package site.addzero.util.ddlgenerator

import org.junit.jupiter.api.Test
import site.addzero.util.db.DatabaseType
import site.addzero.util.ddlgenerator.extension.toCreateTableDDL
import site.addzero.util.lsi.clazz.guessTableName
import site.addzero.util.lsi.database.databaseFields


class DebugTest {
    @Test
    fun `debug DDL generation`() {
        // 创建mock user
        val sysUser = createMockSysUser()

        println("=== SysUser LsiClass Info ===")
        println("Name: ${sysUser.name}")
        println("Table: ${sysUser.guessTableName}")
        println("Fields count: ${sysUser.fields.size}")
        sysUser.fields.forEach { field ->
            println("  - ${field.name}: ${field.typeName} (nullable: ${field.isNullable})")
        }

        println("\n=== Database Fields ===")
        println("Database fields count: ${sysUser.databaseFields.size}")
        sysUser.databaseFields.forEach { field ->
            println("  - ${field.name}: ${field.typeName}")
        }

        println("\n=== Generated DDL ===")
        try {
            val ddl = sysUser.toCreateTableDDL(DatabaseType.MYSQL)
            println(ddl)
        } catch (e: Exception) {
            println("Error: ${e.message}")
            e.printStackTrace()
        }
    }
}

package site.addzero.util.ddlgenerator

import org.junit.jupiter.api.Test
import site.addzero.util.db.DatabaseType
import site.addzero.util.ddlgenerator.extension.toCreateTableDDL

class SimpleDebugTest {
    @Test
    fun `simple test`() {
        val sysUser = createMockSysUser()

        println("Fields: ${sysUser.fields.size}")
        sysUser.fields.forEach { f ->
            println(" - ${f.name}")
        }

        val ddl = sysUser.toCreateTableDDL(DatabaseType.MYSQL)
        println("\nDDL:")
        println(ddl)
    }
}

package site.addzero.split.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class CommonFolderPrefixCleanerTest {

    @Test
    fun `infers compose native component prefix from compose folder names`() {
        val prefix = CommonFolderPrefixCleaner.inferMostRepeatedPrefix(
            listOf(
                "compose_native_component_assist",
                "compose_native_component_button",
                "compose_native_component_card",
                "compose_native_component_table",
                "compose_native_component_table_core",
                "compose_native_component_table_pro",
                "compose_workbench_design",
                "compose_workbench_shell",
                "compose_eventbus",
                "glass_components",
            ),
        )

        assertEquals("compose_native_component_", prefix?.prefix)
    }

    @Test
    fun `rewrites package references without touching longer sibling package names`() {
        val mappings = listOf(
            CommonFolderPrefixCleaner.PackageMapping(
                oldPackage = "site.addzero.compose.compose_native_component_table",
                newPackage = "site.addzero.compose.table",
            ),
        )
        val content = """
            package site.addzero.compose.compose_native_component_table.component

            import site.addzero.compose.compose_native_component_table.Table
            import site.addzero.compose.compose_native_component_table_core.CoreTable

            val ref = site.addzero.compose.compose_native_component_table.Table()
        """.trimIndent()

        val updated = CommonFolderPrefixCleaner.rewritePackageReferences(content, mappings)

        assertEquals(
            """
            package site.addzero.compose.table.component

            import site.addzero.compose.table.Table
            import site.addzero.compose.compose_native_component_table_core.CoreTable

            val ref = site.addzero.compose.table.Table()
            """.trimIndent(),
            updated,
        )
        assertFalse(updated.contains("site.addzero.compose.table_core"))
    }

    @Test
    fun `derives package prefix from multiplatform source root`() {
        val root = Files.createTempDirectory("common-prefix-cleaner").toFile()
        val directory = root
            .resolve("src/commonMain/kotlin/site/addzero/compose/compose_native_component_button")
            .apply { mkdirs() }

        assertEquals(
            "site.addzero.compose.compose_native_component_button",
            CommonFolderPrefixCleaner.packagePrefixForDirectory(directory),
        )
    }

    @Test
    fun `returns null package prefix for invalid package segment`() {
        val root = Files.createTempDirectory("common-prefix-cleaner").toFile()
        val directory = root
            .resolve("src/main/kotlin/site/addzero/2bad")
            .apply { mkdirs() }

        assertNull(CommonFolderPrefixCleaner.packagePrefixForDirectory(directory))
    }
}

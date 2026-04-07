package site.addzero.kcp.transformoverload.idea

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.jetbrains.kotlin.name.FqName

class TransformOverloadResolveExtensionTest {

    @Test
    fun `generated file carries package and callable metadata for resolve extension`() {
        val generatedFile = IdeGeneratedFile(
            relativePath = "site/addzero/example/TransformOverloadStubs.kt",
            packageName = "site.addzero.example",
            topLevelCallableNames = linkedSetOf("sout"),
            topLevelClassifierNames = emptySet(),
            content = """
                @file:Suppress("unused")
                package site.addzero.example
                
                fun site.addzero.example.SoutExample.sout(
                    t: site.addzero.example.S,
                    r: site.addzero.example.R,
                ): kotlin.String = kotlin.error("stub")
            """.trimIndent(),
        )

        val extension = TransformOverloadResolveExtensionProvider()
        val file = generatedFile

        assertEquals(FqName("site.addzero.example"), FqName(file.packageName))
        assertTrue(file.topLevelCallableNames.contains("sout"))
        assertTrue(file.topLevelClassifierNames.isEmpty())
        assertTrue(extension.javaClass.simpleName.isNotBlank())
    }
}

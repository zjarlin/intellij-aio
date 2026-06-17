package site.addzero.cargo.buddy.automod

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AzAutomodBridgeAnalyzerTest {
    @Test
    fun `detects child bridge file already covered by crate root scan`() {
        val crate = Files.createTempDirectory("cargo-buddy-bridge")
        try {
            val src = crate.resolve("src").createDirectories()
            val lib = src.resolve("lib.rs")
            val bridge = src.resolve("entity.rs")
            src.resolve("entity").createDirectories()
            src.resolve("entity/user.rs").writeText("")
            lib.writeText("automod::dir!(pub \"src\");")
            bridge.writeText("automod::dir!(pub(crate) \"src/entity\");")

            val result = AzAutomodBridgeAnalyzer.redundantBridge(
                file = bridge,
                source = bridge.toFile().readText(),
                manifestDirectory = crate,
                rootSources = listOf(RootSource(lib, lib.toFile().readText())),
            )

            assertNotNull(result)
            assertTrue(result!!.visibilityChanges)
        } finally {
            crate.toFile().deleteRecursively()
        }
    }

    @Test
    fun `ignores bridge file with real code`() {
        val crate = Files.createTempDirectory("cargo-buddy-bridge")
        try {
            val src = crate.resolve("src").createDirectories()
            val lib = src.resolve("lib.rs")
            val bridge = src.resolve("entity.rs")
            src.resolve("entity").createDirectories()
            src.resolve("entity/user.rs").writeText("")
            lib.writeText("automod::dir!(pub \"src\");")
            bridge.writeText(
                """
                automod::dir!(pub(crate) "src/entity");

                pub use user::User;
                """.trimIndent(),
            )

            val result = AzAutomodBridgeAnalyzer.redundantBridge(
                file = bridge,
                source = bridge.toFile().readText(),
                manifestDirectory = crate,
                rootSources = listOf(RootSource(lib, lib.toFile().readText())),
            )

            assertNull(result)
        } finally {
            crate.toFile().deleteRecursively()
        }
    }
}

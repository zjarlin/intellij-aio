package site.addzero.cargo.buddy.automod

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AzAutomodExpanderTest {

    @Test
    fun `expands files and directories using az automod rules`() {
        val crate = Files.createTempDirectory("cargo-buddy-automod")
        try {
            val src = crate.resolve("src").createDirectories()
            src.resolve("api.rs").writeText("")
            src.resolve("lib.rs").writeText("")
            src.resolve("main.rs").writeText("")
            src.resolve("mod.rs").writeText("")
            src.resolve("feature-set.rs").writeText("")
            src.resolve("2fast.rs").writeText("")
            src.resolve("nested").createDirectories()
            src.resolve("nested/item.rs").writeText("")
            src.resolve("nested/deeper").createDirectories()
            src.resolve("nested/deeper/value.rs").writeText("")
            src.resolve("owned").createDirectories()
            src.resolve("owned.rs").writeText("")
            src.resolve("owned/skipped.rs").writeText("")
            src.resolve("bin").createDirectories()
            src.resolve("bin/tool.rs").writeText("")

            val result = AzAutomodExpander.expandText(
                source = "automod::dir!(pub \"src\");\n",
                manifestDirectory = crate,
            )

            assertEquals(1, result.expansionCount)
            assertEquals(
                """
                #[path = "2fast.rs"]
                pub mod _2fast;
                pub mod api;
                #[path = "feature-set.rs"]
                pub mod feature_set;
                pub mod nested {
                    pub mod deeper {
                        pub mod value;
                    }
                    pub mod item;
                }
                pub mod owned;

                """.trimIndent(),
                result.expandedText,
            )
        } finally {
            crate.toFile().deleteRecursively()
        }
    }

    @Test
    fun `preserves pub crate visibility`() {
        val crate = Files.createTempDirectory("cargo-buddy-automod")
        try {
            crate.resolve("src/entity").createDirectories()
            crate.resolve("src/entity/user.rs").writeText("")

            val result = AzAutomodExpander.expandText(
                source = "automod::dir!(pub(crate) \"src/entity\")",
                manifestDirectory = crate,
            )

            assertEquals("pub(crate) mod user;", result.expandedText)
        } finally {
            crate.toFile().deleteRecursively()
        }
    }

    @Test
    fun `detects automod calls`() {
        assertTrue(AzAutomodExpander.hasAutomodCall("automod::dir!(\"src\");"))
    }
}

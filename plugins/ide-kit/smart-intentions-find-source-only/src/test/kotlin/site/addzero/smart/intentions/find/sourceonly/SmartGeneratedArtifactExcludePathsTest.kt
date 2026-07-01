package site.addzero.smart.intentions.find.sourceonly

import org.junit.Assert.assertEquals
import org.junit.Test

class SmartGeneratedArtifactExcludePathsTest {
    @Test
    fun `collect module exclude urls for gradle generated directories`() {
        val urls = SmartGeneratedArtifactExcludePaths.collectModuleExcludeUrls(
            null,
            arrayOf(
                "file:///repo",
                "file:///repo/plugins/ide-kit",
            ),
        )

        assertEquals(
            listOf(
                "file:///repo/.gradle",
                "file:///repo/.kotlin",
                "file:///repo/.gradle-user-home",
                "file:///repo/build/tmp",
                "file:///repo/plugins/ide-kit/.gradle",
                "file:///repo/plugins/ide-kit/.kotlin",
                "file:///repo/plugins/ide-kit/.gradle-user-home",
                "file:///repo/plugins/ide-kit/build/tmp",
            ),
            urls,
        )
    }

    @Test
    fun `collect module exclude urls for source only search`() {
        val urls = SmartGeneratedArtifactExcludePaths.collectModuleExcludeUrls(
            null,
            arrayOf("file:///repo/app"),
            sourceOnlySearchEnabled = true,
        )

        assertEquals(
            listOf(
                "file:///repo/app/.gradle",
                "file:///repo/app/.kotlin",
                "file:///repo/app/.gradle-user-home",
                "file:///repo/app/build",
                "file:///repo/app/out",
                "file:///repo/app/target",
                "file:///repo/app/generated",
                "file:///repo/app/src/generated",
                "file:///repo/app/src/main/generated",
                "file:///repo/app/src/test/generated",
            ),
            urls,
        )
    }

    @Test
    fun `deduplicate trailing slash roots`() {
        val urls = SmartGeneratedArtifactExcludePaths.collectModuleExcludeUrls(
            null,
            arrayOf(
                "file:///repo/",
                "file:///repo",
            ),
        )

        assertEquals(
            listOf(
                "file:///repo/.gradle",
                "file:///repo/.kotlin",
                "file:///repo/.gradle-user-home",
                "file:///repo/build/tmp",
            ),
            urls,
        )
    }

    @Test
    fun `project source only excludes generated root directories`() {
        val urls = SmartGeneratedArtifactExcludePaths.collectProjectExcludeUrls(
            "/repo",
            sourceOnlySearchEnabled = true,
        )

        assertEquals(
            listOf(
                "file:///repo/.gradle",
                "file:///repo/.kotlin",
                "file:///repo/.gradle-user-home",
                "file:///repo/build",
                "file:///repo/out",
                "file:///repo/target",
                "file:///repo/generated",
                "file:///repo/src/generated",
                "file:///repo/src/main/generated",
                "file:///repo/src/test/generated",
            ),
            urls,
        )
    }
}

package site.addzero.smart.intentions.find.sourceonly

import org.junit.Assert.assertEquals
import org.junit.Test

class SmartGeneratedArtifactExcludePathsTest {
    @Test
    fun `collect module exclude urls for gradle generated directories`() {
        val urls = SmartGeneratedArtifactExcludePaths.collectModuleExcludeUrls(
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
    fun `deduplicate trailing slash roots`() {
        val urls = SmartGeneratedArtifactExcludePaths.collectModuleExcludeUrls(
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
}

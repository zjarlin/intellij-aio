package site.addzero.recentprojectcleaner

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentProjectPathSupportTest {

    @Test
    fun `missing local path is invalid and existing local path is kept`() {
        val existing = Files.createTempDirectory("recent-project-cleaner-test")
        val missing = existing.resolveSibling("${existing.fileName}-missing")

        try {
            assertFalse(RecentProjectPathSupport.isInvalidLocalPath(existing.toString()))
            assertTrue(RecentProjectPathSupport.isInvalidLocalPath(missing.toString()))
            assertEquals(
                listOf(missing.toString()),
                RecentProjectPathSupport.findInvalidLocalPaths(listOf(existing.toString(), missing.toString())),
            )
        } finally {
            existing.toFile().deleteRecursively()
        }
    }

    @Test
    fun `blank entries are treated as invalid`() {
        assertEquals(listOf(""), RecentProjectPathSupport.findInvalidLocalPaths(listOf("")))
    }

    @Test
    fun `remote and foreign os paths are ignored`() {
        val foreignPath = if (File.separatorChar == '\\') {
            "/Users/test/project"
        } else {
            "C:\\Users\\test\\project"
        }

        assertFalse(RecentProjectPathSupport.isInvalidLocalPath("ssh://remote-host/workspace/project"))
        assertFalse(RecentProjectPathSupport.isInvalidLocalPath(foreignPath))
        assertEquals(
            emptyList<String>(),
            RecentProjectPathSupport.findInvalidLocalPaths(
                listOf("ssh://remote-host/workspace/project", foreignPath),
            ),
        )
    }
}

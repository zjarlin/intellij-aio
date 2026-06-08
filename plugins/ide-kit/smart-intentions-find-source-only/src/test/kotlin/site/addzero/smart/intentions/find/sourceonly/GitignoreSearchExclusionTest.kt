package site.addzero.smart.intentions.find.sourceonly

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class GitignoreSearchExclusionTest {
    @Rule
    @JvmField
    val temp = TemporaryFolder()

    @Test
    fun `collects gitignore directory excludes for normal and nested content roots`() {
        val repo = temp.newFolder("repo").toPath()
        Files.write(
            repo.resolve(".gitignore"),
            listOf(
                ".kmp-buddy/",
                "build",
                "*.zip",
            ),
        )
        val sandboxRoot = repo.resolve(".kmp-buddy/preview-sandbox/App-123")
        val moduleRoot = repo.resolve("plugins/ide-kit")
        Files.createDirectories(sandboxRoot)
        Files.createDirectories(moduleRoot.resolve("build"))

        val urls = GitignoreSearchExclusion.collectDirectoryExcludeUrls(
            repo.toString(),
            arrayOf(
                sandboxRoot.toUri().toASCIIString(),
                moduleRoot.toUri().toASCIIString(),
            ),
        )

        assertEquals(
            listOf(
                pathToFileUrl(sandboxRoot),
                pathToFileUrl(moduleRoot.resolve(".kmp-buddy")),
                pathToFileUrl(moduleRoot.resolve("build")),
            ),
            urls,
        )
    }

    @Test
    fun `collects project gitignore directory excludes`() {
        val repo = temp.newFolder("repo").toPath()
        Files.write(
            repo.resolve(".gitignore"),
            listOf(
                ".kmp-buddy/",
                ".gradle",
                "build",
                "AGENTS.md",
                "*.zip",
            ),
        )
        Files.createDirectories(repo.resolve(".kmp-buddy"))
        Files.createDirectories(repo.resolve(".gradle"))
        Files.createDirectories(repo.resolve("build"))
        Files.write(repo.resolve("AGENTS.md"), listOf("local instructions"))

        val urls = GitignoreSearchExclusion.collectProjectExcludeUrls(repo.toString())

        assertEquals(
            listOf(
                pathToFileUrl(repo.resolve(".kmp-buddy")),
                pathToFileUrl(repo.resolve(".gradle")),
                pathToFileUrl(repo.resolve("build")),
            ),
            urls,
        )
    }

    @Test
    fun `matches ignored paths and later negation rules`() {
        val repo = temp.newFolder("repo").toPath()
        val rules = listOf(
            GitignoreRule.parse(".kmp-buddy/", repo),
            GitignoreRule.parse("*.zip", repo),
            GitignoreRule.parse("!gradle/wrapper/gradle-wrapper.jar", repo),
        ).filterNotNull()

        assertIgnored(rules, repo.resolve(".kmp-buddy/preview-sandbox/App/src/App.kt"))
        assertIgnored(rules, repo.resolve("plugins/ide-kit/build/distributions/ide-kit.zip"))
        assertNotIgnored(rules, repo.resolve("gradle/wrapper/gradle-wrapper.jar"))
        assertNotIgnored(rules, repo.resolve("plugins/ide-kit/src/main/kotlin/App.kt"))
    }

    private fun assertIgnored(rules: List<GitignoreRule>, path: Path) {
        assertTrue(isIgnored(rules, path))
    }

    private fun assertNotIgnored(rules: List<GitignoreRule>, path: Path) {
        assertFalse(isIgnored(rules, path))
    }

    private fun isIgnored(rules: List<GitignoreRule>, path: Path): Boolean {
        var ignored = false
        rules.forEach { rule ->
            if (rule.matches(path, directory = Files.isDirectory(path))) {
                ignored = !rule.negated
            }
        }
        return ignored
    }

    private fun pathToFileUrl(path: Path): String {
        return path.normalize().toUri().toASCIIString().trimEnd('/')
    }
}

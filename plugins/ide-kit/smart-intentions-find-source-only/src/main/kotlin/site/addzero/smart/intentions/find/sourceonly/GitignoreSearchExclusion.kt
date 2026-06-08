package site.addzero.smart.intentions.find.sourceonly

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

internal class GitignoreSearchExclusion private constructor(
    private val projectBasePath: Path?,
) {
    private val ruleCache = mutableMapOf<Path, List<GitignoreRule>>()

    fun isIgnored(file: VirtualFile): Boolean {
        if (!file.isInLocalFileSystem) {
            return false
        }
        val basePath = projectBasePath ?: return false
        val path = Paths.get(file.path).normalize()
        if (!path.startsWith(basePath)) {
            return false
        }

        var ignored = false
        gitignoreFilesFor(path, basePath).forEach { gitignoreFile ->
            rulesFor(gitignoreFile).forEach { rule ->
                if (rule.matches(path, Files.isDirectory(path))) {
                    ignored = !rule.negated
                }
            }
        }
        return ignored
    }

    private fun gitignoreFilesFor(path: Path, basePath: Path): List<Path> {
        val parent = if (Files.isDirectory(path)) path else path.parent ?: return emptyList()
        val directories = generateSequence(parent) { current ->
            if (current == basePath) {
                null
            } else {
                current.parent
            }
        }.toList().asReversed()

        return directories.map { it.resolve(GITIGNORE_FILE_NAME) }
            .filter { Files.isRegularFile(it) }
    }

    private fun rulesFor(gitignoreFile: Path): List<GitignoreRule> {
        return ruleCache.getOrPut(gitignoreFile.normalize()) {
            readRules(gitignoreFile)
        }
    }

    companion object {
        private const val GITIGNORE_FILE_NAME = ".gitignore"
        private const val FILE_URL_PREFIX = "file:"

        fun fromProject(project: Project): GitignoreSearchExclusion {
            return GitignoreSearchExclusion(project.basePath?.let { Paths.get(it).normalize() })
        }

        fun collectDirectoryExcludeUrls(projectBasePath: String?, contentRootUrls: Array<String>): List<String> {
            val projectBase = projectBasePath?.let { Paths.get(it).normalize() }
            return contentRootUrls.asSequence()
                .mapNotNull { fileUrlToPath(it)?.normalize() }
                .flatMap { contentRoot ->
                    collectGitignoreFilesForContentRoot(contentRoot, projectBase).asSequence()
                        .flatMap { gitignoreFile ->
                            readRules(gitignoreFile).asSequence()
                                .flatMap { rule -> rule.toDirectoryExcludePaths(contentRoot) }
                        }
                }
                .distinct()
                .map { path -> pathToFileUrl(path) }
                .toList()
        }

        fun collectProjectExcludeUrls(projectBasePath: String?): List<String> {
            val projectBase = projectBasePath?.let { Paths.get(it).normalize() } ?: return emptyList()
            return readRules(projectBase.resolve(GITIGNORE_FILE_NAME)).asSequence()
                .flatMap { rule -> rule.toDirectoryExcludePaths(projectBase) }
                .distinct()
                .map { path -> pathToFileUrl(path) }
                .toList()
        }

        internal fun readRules(gitignoreFile: Path): List<GitignoreRule> {
            if (!Files.isRegularFile(gitignoreFile)) {
                return emptyList()
            }
            val basePath = gitignoreFile.parent?.normalize() ?: return emptyList()
            return Files.readAllLines(gitignoreFile, StandardCharsets.UTF_8)
                .mapNotNull { GitignoreRule.parse(it, basePath) }
        }

        private fun fileUrlToPath(url: String): Path? {
            if (!url.startsWith(FILE_URL_PREFIX)) {
                return null
            }
            return runCatching { Paths.get(URI(url)) }.getOrNull()
        }

        private fun collectGitignoreFilesForContentRoot(contentRoot: Path, projectBase: Path?): List<Path> {
            val searchBase = projectBase?.takeIf { contentRoot.startsWith(it) } ?: contentRoot
            val relativePath = searchBase.relativize(contentRoot)
            val directories = mutableListOf(searchBase)
            var current = searchBase
            relativePath.forEach { part ->
                current = current.resolve(part)
                directories.add(current)
            }

            return directories.map { it.resolve(GITIGNORE_FILE_NAME) }
                .filter { Files.isRegularFile(it) }
        }

        private fun pathToFileUrl(path: Path): String {
            return path.normalize().toUri().toASCIIString().trimEnd('/')
        }
    }
}

internal data class GitignoreRule(
    val basePath: Path,
    val pattern: String,
    val negated: Boolean,
    val directoryOnly: Boolean,
) {
    private val normalizedPattern = pattern.trim('/').replace('\\', '/')
    private val rooted = pattern.startsWith("/")
    private val containsSlash = normalizedPattern.contains("/")
    private val matcher = Regex(buildRegex())

    fun matches(path: Path, directory: Boolean): Boolean {
        val normalizedPath = path.normalize()
        if (!normalizedPath.startsWith(basePath)) {
            return false
        }
        val relativePath = basePath.relativize(normalizedPath).joinToString("/")
        if (relativePath.isEmpty()) {
            return false
        }
        if (directoryOnly && !directory && !matcher.matches(relativePath)) {
            return false
        }
        return matcher.matches(relativePath)
    }

    fun toDirectoryExcludePaths(contentRoot: Path): Sequence<Path> {
        if (negated || normalizedPattern.isBlank()) {
            return emptySequence()
        }

        val normalizedContentRoot = contentRoot.normalize()
        if (matches(normalizedContentRoot, directory = true)) {
            return sequenceOf(normalizedContentRoot)
        }
        if (hasGlob(normalizedPattern)) {
            return emptySequence()
        }

        val candidate = if (rooted || containsSlash) {
            basePath.resolve(normalizedPattern).normalize()
        } else {
            normalizedContentRoot.resolve(normalizedPattern).normalize()
        }
        if (candidate.startsWith(normalizedContentRoot)) {
            if (!directoryOnly && !Files.isDirectory(candidate)) {
                return emptySequence()
            }
            return sequenceOf(candidate)
        }
        if (normalizedContentRoot.startsWith(candidate)) {
            return sequenceOf(normalizedContentRoot)
        }
        return emptySequence()
    }

    private fun buildRegex(): String {
        val patternRegex = globToRegex(normalizedPattern)
        return if (rooted || containsSlash) {
            "^$patternRegex($|/.*)"
        } else {
            "(^|.*/)$patternRegex($|/.*)"
        }
    }

    companion object {
        fun parse(line: String, basePath: Path): GitignoreRule? {
            val trimmedLine = line.trim()
            if (trimmedLine.isBlank() || trimmedLine.startsWith("#")) {
                return null
            }

            val negated = trimmedLine.startsWith("!")
            val rawPattern = if (negated) trimmedLine.drop(1) else trimmedLine
            val directoryOnly = rawPattern.endsWith("/")
            val normalizedPattern = rawPattern.trimEnd('/')
            if (normalizedPattern.isBlank()) {
                return null
            }

            return GitignoreRule(
                basePath = basePath.normalize(),
                pattern = normalizedPattern,
                negated = negated,
                directoryOnly = directoryOnly,
            )
        }

        private fun hasGlob(pattern: String): Boolean {
            return pattern.any { it == '*' || it == '?' || it == '[' }
        }

        private fun globToRegex(glob: String): String {
            val builder = StringBuilder()
            var index = 0
            while (index < glob.length) {
                val char = glob[index]
                when (char) {
                    '*' -> {
                        val doubleStar = index + 1 < glob.length && glob[index + 1] == '*'
                        if (doubleStar) {
                            val slashAfter = index + 2 < glob.length && glob[index + 2] == '/'
                            if (slashAfter) {
                                builder.append("(?:.*/)?")
                                index += 2
                            } else {
                                builder.append(".*")
                                index += 1
                            }
                        } else {
                            builder.append("[^/]*")
                        }
                    }
                    '?' -> builder.append("[^/]")
                    '.', '(', ')', '+', '|', '^', '$', '@', '%', '{', '}', '[', ']' -> {
                        builder.append('\\').append(char)
                    }
                    '\\' -> {
                        if (index + 1 < glob.length) {
                            builder.append(Regex.escape(glob[index + 1].toString()))
                            index += 1
                        } else {
                            builder.append("\\\\")
                        }
                    }
                    else -> builder.append(char)
                }
                index += 1
            }
            return builder.toString()
        }
    }
}

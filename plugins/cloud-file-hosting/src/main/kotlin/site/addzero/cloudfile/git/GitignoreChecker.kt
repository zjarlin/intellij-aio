package site.addzero.cloudfile.git

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.PathMatcher

/**
 * Checks if files are ignored by .gitignore (project-level and global)
 */
class GitignoreChecker(private val project: Project) {

    private val logger = Logger.getInstance(GitignoreChecker::class.java)
    private val projectBasePath = project.basePath ?: ""

    // Cache for gitignore patterns
    private var gitignorePatterns: List<GitignorePattern> = emptyList()
    private var globalGitignorePatterns: List<GitignorePattern> = emptyList()
    private var lastLoaded: Long = 0

    data class GitignorePattern(
        val pattern: String,
        val isNegation: Boolean,
        val isDirectoryOnly: Boolean,
        val basePath: String
    )

    /**
     * Check if a file is ignored by gitignore
     */
    fun isIgnored(file: File): Boolean {
        if (projectBasePath.isEmpty()) return false

        // Reload if cache is stale (older than 5 seconds)
        if (System.currentTimeMillis() - lastLoaded > 5000) {
            loadGitignorePatterns()
        }

        val relativePath = file.relativeTo(File(projectBasePath)).path
        return isIgnoredByPatterns(relativePath, file.isDirectory)
    }

    /**
     * Check if a path is ignored (for virtual files)
     */
    fun isIgnored(relativePath: String, isDirectory: Boolean = false): Boolean {
        if (projectBasePath.isEmpty()) return false

        if (System.currentTimeMillis() - lastLoaded > 5000) {
            loadGitignorePatterns()
        }

        return isIgnoredByPatterns(relativePath, isDirectory)
    }

    private fun isIgnoredByPatterns(relativePath: String, isDirectory: Boolean): Boolean {
        var ignored = false

        // Check project .gitignore first
        for (pattern in gitignorePatterns) {
            if (matchesPattern(relativePath, isDirectory, pattern)) {
                ignored = !pattern.isNegation
            }
        }

        // If not ignored by project, check global .gitignore
        if (!ignored) {
            for (pattern in globalGitignorePatterns) {
                if (matchesPattern(relativePath, isDirectory, pattern)) {
                    ignored = !pattern.isNegation
                }
            }
        }

        return ignored
    }

    private fun matchesPattern(path: String, isDirectory: Boolean, pattern: GitignorePattern): Boolean {
        // Directory-only patterns
        if (pattern.isDirectoryOnly && !isDirectory) {
            return false
        }

        val cleanPattern = pattern.pattern
            .removeSuffix("/")
            .removePrefix("/")

        // Exact match
        if (path == cleanPattern || path == pattern.pattern) {
            return true
        }

        // Handle patterns with wildcards
        if (cleanPattern.contains("*") || cleanPattern.contains("?")) {
            return matchWildcard(path, cleanPattern, pattern.basePath)
        }

        // Handle directory nesting
        if (path.startsWith(cleanPattern + "/")) {
            return true
        }

        // Handle patterns that match any directory level
        if (!cleanPattern.contains("/")) {
            // Pattern like "build" should match "build", "src/build", etc.
            return path == cleanPattern ||
                   path.startsWith(cleanPattern + "/") ||
                   path.contains("/" + cleanPattern + "/") ||
                   path.endsWith("/" + cleanPattern)
        }

        return false
    }

    private fun matchWildcard(path: String, pattern: String, basePath: String): Boolean {
        return try {
            // Convert gitignore pattern to glob pattern
            val globPattern = when {
                pattern.contains("/") -> {
                    // Pattern with path separator - match from project root
                    "glob:$pattern"
                }
                else -> {
                    // Pattern without path separator - match at any level
                    "glob:**/$pattern"
                }
            }

            val matcher = FileSystems.getDefault().getPathMatcher(globPattern)
            val pathObj = java.nio.file.Paths.get(path)
            matcher.matches(pathObj)
        } catch (e: Exception) {
            logger.warn("Failed to match pattern: $pattern", e)
            false
        }
    }

    private fun loadGitignorePatterns() {
        gitignorePatterns = loadProjectGitignore()
        globalGitignorePatterns = loadGlobalGitignore()
        lastLoaded = System.currentTimeMillis()
    }

    private fun loadProjectGitignore(): List<GitignorePattern> {
        val patterns = mutableListOf<GitignorePattern>()
        val gitignoreFile = File(projectBasePath, ".gitignore")

        if (gitignoreFile.exists()) {
            patterns.addAll(parseGitignoreFile(gitignoreFile, projectBasePath))
        }

        // Also check .git/info/exclude
        val excludeFile = File(projectBasePath, ".git/info/exclude")
        if (excludeFile.exists()) {
            patterns.addAll(parseGitignoreFile(excludeFile, projectBasePath))
        }

        return patterns
    }

    private fun loadGlobalGitignore(): List<GitignorePattern> {
        val patterns = mutableListOf<GitignorePattern>()

        // Try to find global gitignore
        val globalGitignorePaths = listOf(
            System.getProperty("user.home") + "/.gitignore_global",
            System.getProperty("user.home") + "/.gitignore",
            System.getProperty("user.home") + "/.config/git/ignore"
        )

        for (path in globalGitignorePaths) {
            val file = File(path)
            if (file.exists()) {
                patterns.addAll(parseGitignoreFile(file, projectBasePath))
            }
        }

        // Also try to get from git config
        try {
            val process = ProcessBuilder("git", "config", "--global", "core.excludesfile")
                .directory(File(projectBasePath))
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            if (output.isNotEmpty() && File(output).exists()) {
                patterns.addAll(parseGitignoreFile(File(output), projectBasePath))
            }
        } catch (e: Exception) {
            // Git not available or other error
        }

        return patterns
    }

    private fun parseGitignoreFile(file: File, basePath: String): List<GitignorePattern> {
        val patterns = mutableListOf<GitignorePattern>()

        try {
            file.readLines().forEach { line ->
                val trimmed = line.trim()

                // Skip empty lines and comments
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    return@forEach
                }

                // Handle negation patterns (!)
                val isNegation = trimmed.startsWith("!")
                var pattern = if (isNegation) trimmed.substring(1) else trimmed

                // Handle escaped exclamation mark
                if (pattern.startsWith("\\!")) {
                    pattern = pattern.substring(1)
                }

                // Handle escaped hash
                if (pattern.startsWith("\\#")) {
                    pattern = pattern.substring(1)
                }

                // Check if directory-only pattern (ends with /)
                val isDirectoryOnly = pattern.endsWith("/")

                patterns.add(GitignorePattern(
                    pattern = pattern,
                    isNegation = isNegation,
                    isDirectoryOnly = isDirectoryOnly,
                    basePath = basePath
                ))
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse gitignore file: ${file.path}", e)
        }

        return patterns
    }

    companion object {
        fun getInstance(project: Project): GitignoreChecker {
            return project.getService(GitignoreChecker::class.java)
        }
    }
}

package site.addzero.recentprojectcleaner

import java.io.File
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths

internal object RecentProjectPathSupport {
    private val uriPrefix = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
    private val windowsDrivePrefix = Regex("^[a-zA-Z]:[\\\\/].*")

    fun findInvalidLocalPaths(
        paths: Iterable<String>,
        pathExists: (Path) -> Boolean = Files::exists,
    ): List<String> {
        return paths.filter { isInvalidLocalPath(it, pathExists) }
    }

    fun isInvalidLocalPath(
        rawPath: String,
        pathExists: (Path) -> Boolean = Files::exists,
    ): Boolean {
        if (rawPath.isBlank()) {
            return true
        }

        if (uriPrefix.containsMatchIn(rawPath) || isForeignOsPath(rawPath)) {
            return false
        }

        val path = try {
            Paths.get(rawPath)
        } catch (_: InvalidPathException) {
            return true
        }

        if (!path.isAbsolute) {
            return true
        }

        return !pathExists(path)
    }

    private fun isForeignOsPath(rawPath: String): Boolean {
        val isWindows = File.separatorChar == '\\'
        return if (isWindows) {
            rawPath.startsWith("/")
        } else {
            rawPath.startsWith("\\\\") || windowsDrivePrefix.matches(rawPath)
        }
    }
}

package site.addzero.composebuddy.support

import com.intellij.openapi.vfs.VirtualFile
import site.addzero.composebuddy.settings.ComposeBuddySettingsService
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object ComponentLibrarySupport {
    fun getComponentLibraryRootPath(): Path? {
        val configuredPath = ComposeBuddySettingsService.getInstance()
            .state
            .componentLibraryRootPath
            .trim()
            .takeIf { it.isNotEmpty() }
            ?: return null
        return Paths.get(configuredPath).normalize().takeIf(Files::isDirectory)
    }

    fun rememberComponentLibraryRoot(directory: VirtualFile): Boolean {
        if (!directory.isValid || !directory.isDirectory || !directory.isInLocalFileSystem) {
            return false
        }
        ComposeBuddySettingsService.getInstance()
            .state
            .componentLibraryRootPath = Paths.get(directory.path).normalize().toString()
        return true
    }

    fun targetFilePath(
        componentLibraryRootPath: Path,
        packageName: String,
        fileName: String,
    ): Path {
        val packagePath = packageName
            .split(".")
            .filter { it.isNotBlank() }
            .fold(componentLibraryRootPath.normalize()) { path, segment -> path.resolve(segment) }
        return packagePath.resolve(fileName).normalize()
    }
}

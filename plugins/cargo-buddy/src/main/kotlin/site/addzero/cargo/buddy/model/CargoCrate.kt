package site.addzero.cargo.buddy.model

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path
import kotlin.io.path.Path

data class CargoCrate(
    val manifestFile: VirtualFile,
    val rootDirectory: VirtualFile,
    val projectRelativePath: String,
    val workspaceRootDirectory: VirtualFile,
) {
    val manifestPath: String = manifestFile.path
    val rootPath: String = rootDirectory.path
    val workspaceRootPath: String = workspaceRootDirectory.path
    val crateName: String = rootDirectory.name
    val workspaceName: String = workspaceRootDirectory.name
    val displayName: String = if (projectRelativePath.isBlank()) {
        crateName
    } else {
        projectRelativePath
    }

    fun manifestNioPath(): Path = Path(manifestPath)

    companion object {
        fun fromManifest(
            project: Project,
            manifestFile: VirtualFile,
        ): CargoCrate {
            val rootDirectory = requireNotNull(manifestFile.parent) {
                "Cargo manifest has no parent directory: ${manifestFile.path}"
            }
            val basePath = project.basePath.orEmpty()
            val projectRelativePath = rootDirectory.path
                .removePrefix(basePath)
                .trimStart('/')
            return CargoCrate(
                manifestFile = manifestFile,
                rootDirectory = rootDirectory,
                projectRelativePath = projectRelativePath,
                workspaceRootDirectory = findWorkspaceRoot(rootDirectory),
            )
        }

        private fun findWorkspaceRoot(rootDirectory: VirtualFile): VirtualFile {
            var candidate = rootDirectory
            var directory = rootDirectory.parent
            while (directory != null) {
                val manifest = directory.findChild("Cargo.toml")
                if (manifest != null && manifest.isValid && !manifest.isDirectory) {
                    candidate = directory
                }
                directory = directory.parent
            }
            return candidate
        }
    }
}

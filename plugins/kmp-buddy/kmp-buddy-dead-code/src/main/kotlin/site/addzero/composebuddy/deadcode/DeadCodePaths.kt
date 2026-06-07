package site.addzero.composebuddy.deadcode

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

object DeadCodePaths {
    fun projectRoot(project: Project): Path? {
        return project.basePath?.let(Paths::get)
    }

    fun defaultSourceModuleRoot(project: Project, entryFile: VirtualFile?): VirtualFile? {
        val root = projectRoot(project) ?: return null
        val defaultPath = root.resolve(DeadCodeConstants.DEFAULT_SOURCE_MODULE_RELATIVE_PATH)
        if (defaultPath.exists()) {
            LocalFileSystem.getInstance().findFileByNioFile(defaultPath)?.let { return it }
        }

        if (entryFile != null) {
            ProjectFileIndex.getInstance(project).getContentRootForFile(entryFile)?.let { return it }
        }

        return root.let(LocalFileSystem.getInstance()::findFileByNioFile)
    }

    fun mirrorModuleRoot(
        project: Project,
        sourceModuleRoot: VirtualFile,
    ): Path? {
        val root = projectRoot(project) ?: return null
        return root
            .resolve(DeadCodeConstants.MIRROR_ROOT_RELATIVE_PATH)
            .resolve(sourceModuleRoot.path.trim('/').replace("/", "__"))
    }

    fun relativePath(moduleRoot: Path, file: Path): String {
        return moduleRoot.relativize(file).joinToString("/")
    }
}

package site.addzero.composebuddy.previewsandbox

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object PreviewSandboxDependencyClasspath {
    fun collect(previewFile: KtFile): List<String> {
        val virtualFile = previewFile.virtualFile ?: return emptyList()
        val module = ModuleUtilCore.findModuleForFile(virtualFile, previewFile.project) ?: return emptyList()
        val fileIndex = ProjectFileIndex.getInstance(previewFile.project)
        val projectRoot = previewFile.project.basePath
            ?.let { basePath -> runCatching { Paths.get(basePath).toAbsolutePath().normalize() }.getOrNull() }

        return OrderEnumerator.orderEntries(module)
            .recursively()
            .withoutSdk()
            .classes()
            .roots
            .asSequence()
            .filterNot(fileIndex::isInContent)
            .mapNotNull(::toLocalClassPath)
            .filterNot { classPath -> classPath.isUnder(projectRoot) }
            .distinct()
            .sorted()
            .toList()
    }

    private fun toLocalClassPath(root: VirtualFile): String? {
        val normalizedPath = root.path
            .removePrefix("jar://")
            .removeSuffix("!/")
            .removeSuffix("!")
        val path = runCatching { Paths.get(normalizedPath) }.getOrNull() ?: return null
        if (!Files.exists(path)) {
            return null
        }
        if (!Files.isRegularFile(path) && !Files.isDirectory(path)) {
            return null
        }
        return path.toString()
    }

    private fun String.isUnder(root: Path?): Boolean {
        if (root == null) {
            return false
        }
        val path = runCatching { Paths.get(this).toAbsolutePath().normalize() }.getOrNull() ?: return false
        return path.startsWith(root)
    }
}

package site.addzero.composebuddy.deadcode

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.Comparator
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

class DeadCodeRestorer(
    private val project: Project,
) {
    fun restore(manifestPath: Path): DeadCodeRestoreResult {
        val manifest = DeadCodeManifestJson.read(manifestPath)
        val conflicts = mutableListOf<String>()
        var restored = 0
        var missingMirrorFiles = 0

        manifest.movedFiles.forEach { moved ->
            val sourcePath = Paths.get(moved.sourcePath)
            val mirrorPath = Paths.get(moved.mirrorPath)
            if (!mirrorPath.exists()) {
                missingMirrorFiles++
                return@forEach
            }

            if (sourcePath.exists()) {
                val currentSha = DeadCodeFileHash.sha256(sourcePath)
                if (currentSha == moved.sha256) {
                    Files.deleteIfExists(mirrorPath)
                } else {
                    conflicts += moved.relativePath
                }
                return@forEach
            }

            sourcePath.parent.createDirectories()
            Files.move(mirrorPath, sourcePath, StandardCopyOption.REPLACE_EXISTING)
            restored++
        }

        val conflictReportPath = if (conflicts.isNotEmpty() || missingMirrorFiles > 0) {
            val path = manifestPath.parent.resolve(DeadCodeConstants.CONFLICT_REPORT_FILE_NAME)
            Files.write(path, renderConflictReport(conflicts, missingMirrorFiles).toByteArray(Charsets.UTF_8))
            path
        } else {
            cleanupMirrorRoot(manifestPath.parent)
            null
        }

        refresh(Paths.get(manifest.sourceModulePath), Paths.get(manifest.mirrorRootPath))
        return DeadCodeRestoreResult(
            restoredFileCount = restored,
            conflictCount = conflicts.size,
            missingMirrorFileCount = missingMirrorFiles,
            conflictReportPath = conflictReportPath,
        )
    }

    private fun renderConflictReport(
        conflicts: List<String>,
        missingMirrorFiles: Int,
    ): String {
        return buildString {
            appendLine("# KMP Buddy Dead Code Restore Conflicts")
            appendLine()
            if (conflicts.isNotEmpty()) {
                appendLine("The following source files already exist and differ from the original checksum:")
                appendLine()
                conflicts.forEach { relativePath ->
                    appendLine("- `$relativePath`")
                }
                appendLine()
            }
            if (missingMirrorFiles > 0) {
                appendLine("Missing mirror files: $missingMirrorFiles")
            }
        }
    }

    private fun cleanupMirrorRoot(mirrorRoot: Path) {
        Files.walk(mirrorRoot).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { path ->
                if (path == mirrorRoot || Files.isRegularFile(path) || Files.isDirectory(path)) {
                    Files.deleteIfExists(path)
                }
            }
        }
    }

    private fun refresh(vararg paths: Path) {
        val files = paths.mapNotNull { path ->
            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
        }.toTypedArray()
        if (files.isNotEmpty()) {
            VfsUtil.markDirtyAndRefresh(false, true, true, *files)
        }
    }
}

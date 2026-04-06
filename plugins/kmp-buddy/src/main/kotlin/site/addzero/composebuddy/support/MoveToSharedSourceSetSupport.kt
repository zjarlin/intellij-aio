package site.addzero.composebuddy.support

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor
import org.jetbrains.kotlin.psi.KtFile
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.settings.ComposeBuddySettingsService
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths

object MoveToSharedSourceSetSupport {
    data class MovePlan(
        val sourceFile: VirtualFile,
        val targetFilePath: Path,
    ) {
        val targetDirectoryPath: Path
            get() = targetFilePath.parent
    }

    fun collectKotlinFiles(selectedFiles: Collection<VirtualFile>): List<VirtualFile> {
        val collectedFiles = linkedMapOf<String, VirtualFile>()
        selectedFiles.forEach { file ->
            collectKotlinFiles(file, collectedFiles)
        }
        return collectedFiles.values.toList()
    }

    fun buildPlan(file: VirtualFile): MovePlan? {
        if (!file.isValid || file.isDirectory || !file.isInLocalFileSystem) {
            return null
        }
        val sourceLayout = parseSourceLayout(file.path) ?: return null
        val relativeTargetPath = ComposeBuddySettingsService.getInstance().state.sharedMoveTargetRelativePath.trim()
        if (relativeTargetPath.isEmpty()) {
            return null
        }
        val configuredPath = try {
            Paths.get(relativeTargetPath)
        } catch (_: InvalidPathException) {
            return null
        }
        if (configuredPath.isAbsolute) {
            return null
        }
        val moduleRootPath = Paths.get(sourceLayout.moduleRootPath).normalize()
        val currentFilePath = Paths.get(file.path).normalize()
        val relativeFilePath = Paths.get(sourceLayout.relativeFilePath).normalize()
        val targetSourceRootPath = moduleRootPath.resolve(configuredPath).normalize()
        if (currentFilePath.startsWith(targetSourceRootPath)) {
            return null
        }
        val targetFilePath = targetSourceRootPath.resolve(relativeFilePath).normalize()
        if (targetFilePath == currentFilePath) {
            return null
        }
        if (Files.exists(targetFilePath)) {
            return null
        }
        val targetDirectoryPath = targetFilePath.parent ?: return null
        return MovePlan(
            sourceFile = file,
            targetFilePath = targetDirectoryPath.resolve(file.name),
        )
    }

    fun filterConflictingPlans(plans: Collection<MovePlan>): List<MovePlan> {
        val conflictingTargets = plans
            .groupBy { it.targetFilePath.normalize() }
            .filterValues { it.size > 1 }
            .keys
        return plans.filterNot { it.targetFilePath.normalize() in conflictingTargets }
    }

    fun movePlans(project: Project, plans: Collection<MovePlan>): Int {
        if (plans.isEmpty()) {
            return 0
        }
        val psiManager = PsiManager.getInstance(project)
        var movedFileCount = 0
        plans
            .groupBy { it.targetDirectoryPath.normalize() }
            .forEach { (targetDirectoryPath, groupedPlans) ->
                val targetDirectory = createTargetDirectory(project, targetDirectoryPath) ?: return@forEach
                val psiFiles = groupedPlans.mapNotNull { plan ->
                    psiManager.findFile(plan.sourceFile) as? KtFile
                }
                if (psiFiles.isEmpty()) {
                    return@forEach
                }
                MoveFilesOrDirectoriesProcessor(
                    project,
                    psiFiles.toTypedArray(),
                    targetDirectory,
                    false,
                    false,
                    null,
                    null,
                ).run()
                movedFileCount += psiFiles.size
            }
        return movedFileCount
    }

    private fun createTargetDirectory(project: Project, path: Path): PsiDirectory? {
        return WriteCommandAction.writeCommandAction(project)
            .withName(ComposeBuddyBundle.message("command.move.file.to.shared"))
            .compute<PsiDirectory?, RuntimeException> {
                val virtualDirectory = VfsUtil.createDirectoryIfMissing(path.toString()) ?: return@compute null
                PsiManager.getInstance(project).findDirectory(virtualDirectory)
            }
    }

    private fun collectKotlinFiles(file: VirtualFile, sink: MutableMap<String, VirtualFile>) {
        if (!file.isValid || !file.isInLocalFileSystem) {
            return
        }
        if (file.isDirectory) {
            file.children.forEach { child ->
                collectKotlinFiles(child, sink)
            }
            return
        }
        if (file.extension?.equals("kt", ignoreCase = true) != true) {
            return
        }
        sink.putIfAbsent(file.path, file)
    }

    private fun parseSourceLayout(filePath: String): SourceLayout? {
        val normalizedPath = filePath.replace('\\', '/')
        val matchResult = SOURCE_FILE_PATTERN.matchEntire(normalizedPath) ?: return null
        return SourceLayout(
            moduleRootPath = matchResult.groupValues[1],
            relativeFilePath = matchResult.groupValues[2],
        )
    }

    private data class SourceLayout(
        val moduleRootPath: String,
        val relativeFilePath: String,
    )

    private val SOURCE_FILE_PATTERN = Regex("""^(.+)/src/[^/]+Main/kotlin/(.+)$""")
}

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
import java.nio.file.Path
import java.nio.file.Paths

object MoveToSharedSourceSetSupport {
    const val SHARE_LOGIC_SOURCE_SET = "share_logic"
    const val SHARE_UI_SOURCE_SET = "share_ui"

    val SUPPORTED_SHARED_SOURCE_SETS: List<String> = listOf(
        SHARE_LOGIC_SOURCE_SET,
        SHARE_UI_SOURCE_SET,
    )

    data class MovePlan(
        val sourceFile: VirtualFile,
        val targetFilePath: Path,
    ) {
        val targetDirectoryPath: Path
            get() = targetFilePath.parent
    }

    data class SourceLayout(
        val moduleRootPath: Path,
        val sourceSetName: String,
        val relativeFilePath: Path,
    )

    fun collectKotlinFiles(selectedFiles: Collection<VirtualFile>): List<VirtualFile> {
        val collectedFiles = linkedMapOf<String, VirtualFile>()
        selectedFiles.forEach { file ->
            collectKotlinFiles(file, collectedFiles)
        }
        return collectedFiles.values.toList()
    }

    fun parseSourceLayout(file: VirtualFile): SourceLayout? {
        if (!file.isValid || file.isDirectory || !file.isInLocalFileSystem) {
            return null
        }
        val normalizedPath = file.path.replace('\\', '/')
        val matchResult = SOURCE_FILE_PATTERN.matchEntire(normalizedPath) ?: return null
        return SourceLayout(
            moduleRootPath = Paths.get(matchResult.groupValues[1]).normalize(),
            sourceSetName = matchResult.groupValues[2],
            relativeFilePath = Paths.get(matchResult.groupValues[3]).normalize(),
        )
    }

    fun getRememberedSharedSourceSet(moduleRootPath: Path): String? {
        val normalizedModuleRootPath = normalizeModuleRootPath(moduleRootPath)
        return ComposeBuddySettingsService.getInstance()
            .state
            .sharedMoveTargetSourceSetByModuleRoot[normalizedModuleRootPath]
            ?.takeIf(::isSupportedSharedSourceSet)
    }

    fun rememberSharedSourceSet(moduleRootPath: Path, sourceSetName: String): Boolean {
        if (!isSupportedSharedSourceSet(sourceSetName)) {
            return false
        }
        val normalizedModuleRootPath = normalizeModuleRootPath(moduleRootPath)
        ComposeBuddySettingsService.getInstance()
            .state
            .sharedMoveTargetSourceSetByModuleRoot[normalizedModuleRootPath] = sourceSetName
        return true
    }

    fun buildPlan(file: VirtualFile, targetSourceSetName: String): MovePlan? {
        if (!file.isValid || file.isDirectory || !file.isInLocalFileSystem) {
            return null
        }
        if (!isSupportedSharedSourceSet(targetSourceSetName)) {
            return null
        }
        val sourceLayout = parseSourceLayout(file) ?: return null
        val currentFilePath = Paths.get(file.path).normalize()
        val targetSourceRootPath = sourceLayout.moduleRootPath
            .resolve("src")
            .resolve(targetSourceSetName)
            .resolve("kotlin")
            .normalize()
        if (currentFilePath.startsWith(targetSourceRootPath)) {
            return null
        }
        val targetFilePath = targetSourceRootPath.resolve(sourceLayout.relativeFilePath).normalize()
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

    fun buildPlan(file: VirtualFile): MovePlan? {
        val sourceLayout = parseSourceLayout(file) ?: return null
        val targetSourceSetName = getRememberedSharedSourceSet(sourceLayout.moduleRootPath) ?: return null
        return buildPlan(file, targetSourceSetName)
    }

    fun filterConflictingPlans(plans: Collection<MovePlan>): List<MovePlan> {
        val conflictingTargets = plans
            .groupBy { it.targetFilePath.normalize() }
            .filterValues { it.size > 1 }
            .keys
        return plans.filterNot { it.targetFilePath.normalize() in conflictingTargets }
    }

    fun movePlans(
        project: Project,
        plans: Collection<MovePlan>,
        commandName: String = ComposeBuddyBundle.message("command.move.file.to.shared"),
    ): Int {
        if (plans.isEmpty()) {
            return 0
        }
        val psiManager = PsiManager.getInstance(project)
        var movedFileCount = 0
        plans
            .groupBy { it.targetDirectoryPath.normalize() }
            .forEach { (targetDirectoryPath, groupedPlans) ->
                val targetDirectory = createTargetDirectory(project, targetDirectoryPath, commandName) ?: return@forEach
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

    private fun createTargetDirectory(project: Project, path: Path, commandName: String): PsiDirectory? {
        return WriteCommandAction.writeCommandAction(project)
            .withName(commandName)
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

    private fun normalizeModuleRootPath(path: Path): String = path.normalize().toString()

    private fun isSupportedSharedSourceSet(sourceSetName: String): Boolean {
        return sourceSetName in SUPPORTED_SHARED_SOURCE_SETS
    }

    private val SOURCE_FILE_PATTERN = Regex("""^(.+)/src/([^/]+)/kotlin/(.+)$""")
}

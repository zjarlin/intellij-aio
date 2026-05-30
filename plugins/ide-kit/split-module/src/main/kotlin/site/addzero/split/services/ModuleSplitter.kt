package site.addzero.split.services

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtil
import java.io.File

/**
 * 模块拆分服务
 */
class ModuleSplitter(private val project: Project) {

    private val logger = Logger.getInstance(ModuleSplitter::class.java)

    private data class SplitPlan(
        val buildSystem: BuildSystem,
        val projectRoot: File,
        val sourceModuleDir: File,
        val targetModuleDir: File,
        val targetModuleAlreadyExists: Boolean,
        val effectiveSelection: List<File>,
        val conflicts: List<PathConflict>,
    )

    private data class PathConflict(
        val sourcePath: File,
        val targetPath: File,
    )

    private data class SplitExecutionResult(
        val mergedIntoExistingModule: Boolean,
        val overwrittenConflictCount: Int,
        val skippedConflictCount: Int,
        val movedAnyContent: Boolean,
    )

    private enum class ConflictResolution {
        OVERWRITE,
        SKIP,
    }

    private class ConflictStats {
        private val overwrittenTargets = mutableSetOf<String>()
        private val skippedTargets = mutableSetOf<String>()
        private var movedAnyContentInternal = false

        val overwrittenConflictCount: Int
            get() = overwrittenTargets.size

        val skippedConflictCount: Int
            get() = skippedTargets.size

        val movedAnyContent: Boolean
            get() = movedAnyContentInternal

        fun recordOverwrite(targetPath: File) {
            overwrittenTargets += targetPath.absolutePath
        }

        fun recordSkip(targetPath: File) {
            skippedTargets += targetPath.absolutePath
        }

        fun recordMove() {
            movedAnyContentInternal = true
        }
    }

    /**
     * 执行模块拆分
     * @param sourceModule 源模块目录
     * @param selectedFiles 选中的文件列表
     * @param newModuleName 新模块名称
     * @return 是否成功
     */
    fun split(sourceModule: VirtualFile, selectedFiles: List<VirtualFile>, newModuleName: String): Boolean {
        return try {
            val splitPlan = createSplitPlan(sourceModule, selectedFiles, newModuleName)
            val conflictResolutions = resolveConflicts(splitPlan) ?: return false

            lateinit var executionResult: SplitExecutionResult
            WriteCommandAction.runWriteCommandAction(project) {
                executionResult = performSplit(sourceModule, newModuleName, splitPlan, conflictResolutions)
            }

            showSuccessNotification(sourceModule.name, newModuleName, executionResult)
            true
        } catch (e: Exception) {
            logger.error("Failed to split module", e)
            showErrorNotification(e.message ?: "Unknown error")
            false
        }
    }

    private fun createSplitPlan(
        sourceModule: VirtualFile,
        selectedFiles: List<VirtualFile>,
        newModuleName: String,
    ): SplitPlan {
        val projectBasePath = project.basePath
            ?: throw IllegalStateException("未找到项目根目录")
        val projectRoot = File(projectBasePath)
        val sourceModuleDir = File(sourceModule.path)
        val buildSystem = BuildSystem.detect(sourceModule)
        val targetModuleDir = PathCalculator.calculateSiblingPath(sourceModuleDir, newModuleName)

        if (targetModuleDir.canonicalFile == sourceModuleDir.canonicalFile) {
            throw IllegalStateException("目标模块不能与源模块同名")
        }

        if (targetModuleDir.exists() && !targetModuleDir.isDirectory) {
            throw IllegalStateException("目标路径 '$newModuleName' 已存在且不是目录")
        }

        val effectiveSelection = calculateEffectiveSelection(selectedFiles)
        val conflicts = collectConflicts(sourceModuleDir, effectiveSelection, targetModuleDir)

        return SplitPlan(
            buildSystem = buildSystem,
            projectRoot = projectRoot,
            sourceModuleDir = sourceModuleDir,
            targetModuleDir = targetModuleDir,
            targetModuleAlreadyExists = targetModuleDir.exists(),
            effectiveSelection = effectiveSelection,
            conflicts = conflicts,
        )
    }

    private fun calculateEffectiveSelection(selectedFiles: List<VirtualFile>): List<File> {
        val selectedDirs = selectedFiles
            .filter { it.isDirectory }
            .map { File(it.path).toPath() }

        return selectedFiles
            .map { File(it.path) }
            .filter { file ->
                val filePath = file.toPath()
                selectedDirs.none { dirPath ->
                    dirPath != filePath && filePath.startsWith(dirPath)
                }
            }
            .sortedBy { it.path }
    }

    private fun collectConflicts(
        sourceModuleDir: File,
        effectiveSelection: List<File>,
        targetModuleDir: File,
    ): List<PathConflict> {
        if (!targetModuleDir.exists()) {
            return emptyList()
        }

        val conflictsByTargetPath = linkedMapOf<String, PathConflict>()
        effectiveSelection.forEach { sourcePath ->
            val relativePath = sourcePath.relativeTo(sourceModuleDir)
            val targetPath = File(targetModuleDir, relativePath.path)
            collectConflictsRecursively(sourcePath, targetPath, targetModuleDir, conflictsByTargetPath)
        }

        return conflictsByTargetPath.values.toList()
    }

    private fun collectConflictsRecursively(
        sourcePath: File,
        targetPath: File,
        targetModuleDir: File,
        conflictsByTargetPath: MutableMap<String, PathConflict>,
    ) {
        val blockingAncestorConflict = findBlockingAncestorConflict(sourcePath, targetPath, targetModuleDir)
        if (blockingAncestorConflict != null) {
            conflictsByTargetPath.putIfAbsent(
                blockingAncestorConflict.targetPath.absolutePath,
                blockingAncestorConflict,
            )
            return
        }

        if (!targetPath.exists()) {
            return
        }

        if (sourcePath.isDirectory && targetPath.isDirectory) {
            sourcePath
                .listFiles()
                .orEmpty()
                .sortedBy { it.name }
                .forEach { child ->
                    collectConflictsRecursively(
                        child,
                        File(targetPath, child.name),
                        targetModuleDir,
                        conflictsByTargetPath,
                    )
                }
            return
        }

        conflictsByTargetPath.putIfAbsent(
            targetPath.absolutePath,
            PathConflict(sourcePath = sourcePath, targetPath = targetPath),
        )
    }

    private fun findBlockingAncestorConflict(
        sourcePath: File,
        targetPath: File,
        targetModuleDir: File,
    ): PathConflict? {
        var currentSourceParent = sourcePath.parentFile
        var currentTargetParent = targetPath.parentFile

        while (currentTargetParent != null && currentTargetParent != targetModuleDir.parentFile) {
            if (currentTargetParent.exists() && currentTargetParent.isFile) {
                val sourceConflictPath = currentSourceParent ?: sourcePath
                return PathConflict(sourcePath = sourceConflictPath, targetPath = currentTargetParent)
            }

            if (currentTargetParent == targetModuleDir) {
                break
            }

            currentSourceParent = currentSourceParent?.parentFile
            currentTargetParent = currentTargetParent.parentFile
        }

        return null
    }

    private fun resolveConflicts(splitPlan: SplitPlan): Map<String, ConflictResolution>? {
        if (splitPlan.conflicts.isEmpty()) {
            return emptyMap()
        }

        val conflictResolutions = linkedMapOf<String, ConflictResolution>()
        splitPlan.conflicts.forEach { conflict ->
            val relativeTargetPath = conflict.targetPath
                .relativeTo(splitPlan.targetModuleDir)
                .path
                .replace(File.separatorChar, '/')

            val choice = Messages.showDialog(
                project,
                buildConflictMessage(relativeTargetPath, conflict),
                "Split Module 冲突",
                arrayOf("覆盖", "跳过", "取消"),
                0,
                Messages.getWarningIcon(),
            )

            when (choice) {
                0 -> {
                    conflictResolutions[conflict.targetPath.absolutePath] = ConflictResolution.OVERWRITE
                }

                1 -> {
                    conflictResolutions[conflict.targetPath.absolutePath] = ConflictResolution.SKIP
                }

                else -> {
                    return null
                }
            }
        }

        return conflictResolutions
    }

    private fun buildConflictMessage(relativeTargetPath: String, conflict: PathConflict): String {
        val sourceType = if (conflict.sourcePath.isDirectory) {
            "目录"
        } else {
            "文件"
        }
        val targetType = if (conflict.targetPath.isDirectory) {
            "目录"
        } else {
            "文件"
        }

        return buildString {
            appendLine("目标模块中已存在冲突项：$relativeTargetPath")
            appendLine("源项类型：$sourceType")
            appendLine("目标项类型：$targetType")
            append("选择“覆盖”会替换目标项，选择“跳过”会保留源模块中的当前项。")
        }
    }

    private fun performSplit(
        sourceModule: VirtualFile,
        newModuleName: String,
        splitPlan: SplitPlan,
        conflictResolutions: Map<String, ConflictResolution>,
    ): SplitExecutionResult {
        if (!splitPlan.targetModuleDir.exists()) {
            splitPlan.targetModuleDir.mkdirs()
        }

        splitPlan.buildSystem.copyAndAdjustBuildFile(sourceModule, splitPlan.targetModuleDir, newModuleName)

        val conflictStats = ConflictStats()
        splitPlan.effectiveSelection.forEach { sourcePath ->
            val relativePath = sourcePath.relativeTo(splitPlan.sourceModuleDir)
            val targetPath = File(splitPlan.targetModuleDir, relativePath.path)
            mergePath(sourcePath, targetPath, splitPlan.targetModuleDir, conflictResolutions, conflictStats)
        }

        if (conflictStats.movedAnyContent) {
            splitPlan.buildSystem.addDependency(sourceModule, splitPlan.projectRoot, splitPlan.targetModuleDir)
        }

        VfsUtil.markDirtyAndRefresh(false, true, true, splitPlan.sourceModuleDir, splitPlan.targetModuleDir)

        return SplitExecutionResult(
            mergedIntoExistingModule = splitPlan.targetModuleAlreadyExists,
            overwrittenConflictCount = conflictStats.overwrittenConflictCount,
            skippedConflictCount = conflictStats.skippedConflictCount,
            movedAnyContent = conflictStats.movedAnyContent,
        )
    }

    private fun mergePath(
        sourcePath: File,
        targetPath: File,
        targetModuleDir: File,
        conflictResolutions: Map<String, ConflictResolution>,
        conflictStats: ConflictStats,
    ) {
        val blockingAncestorConflict = findBlockingAncestorConflict(sourcePath, targetPath, targetModuleDir)
        if (blockingAncestorConflict != null) {
            if (!applyConflictResolution(blockingAncestorConflict, conflictResolutions, conflictStats)) {
                return
            }
        }

        if (sourcePath.isDirectory) {
            mergeDirectory(sourcePath, targetPath, targetModuleDir, conflictResolutions, conflictStats)
            return
        }

        mergeFile(sourcePath, targetPath, conflictResolutions, conflictStats)
    }

    private fun mergeDirectory(
        sourceDir: File,
        targetDir: File,
        targetModuleDir: File,
        conflictResolutions: Map<String, ConflictResolution>,
        conflictStats: ConflictStats,
    ) {
        if (!targetDir.exists()) {
            sourceDir.copyRecursively(targetDir, overwrite = false)
            sourceDir.deleteRecursively()
            conflictStats.recordMove()
            return
        }

        if (targetDir.isFile) {
            val conflict = PathConflict(sourcePath = sourceDir, targetPath = targetDir)
            if (!applyConflictResolution(conflict, conflictResolutions, conflictStats)) {
                return
            }

            sourceDir.copyRecursively(targetDir, overwrite = false)
            sourceDir.deleteRecursively()
            conflictStats.recordMove()
            return
        }

        sourceDir
            .listFiles()
            .orEmpty()
            .sortedBy { it.name }
            .forEach { child ->
                mergePath(
                    child,
                    File(targetDir, child.name),
                    targetModuleDir,
                    conflictResolutions,
                    conflictStats,
                )
            }

        deleteDirectoryIfEmpty(sourceDir)
    }

    private fun mergeFile(
        sourceFile: File,
        targetFile: File,
        conflictResolutions: Map<String, ConflictResolution>,
        conflictStats: ConflictStats,
    ) {
        if (!targetFile.exists()) {
            targetFile.parentFile?.mkdirs()
            sourceFile.copyTo(targetFile, overwrite = false)
            sourceFile.delete()
            conflictStats.recordMove()
            return
        }

        val conflict = PathConflict(sourcePath = sourceFile, targetPath = targetFile)
        if (!applyConflictResolution(conflict, conflictResolutions, conflictStats)) {
            return
        }

        targetFile.parentFile?.mkdirs()
        sourceFile.copyTo(targetFile, overwrite = false)
        sourceFile.delete()
        conflictStats.recordMove()
    }

    private fun applyConflictResolution(
        conflict: PathConflict,
        conflictResolutions: Map<String, ConflictResolution>,
        conflictStats: ConflictStats,
    ): Boolean {
        val resolution = conflictResolutions[conflict.targetPath.absolutePath]
            ?: throw IllegalStateException("缺少冲突项处理策略：${conflict.targetPath.path}")

        if (resolution == ConflictResolution.SKIP) {
            conflictStats.recordSkip(conflict.targetPath)
            return false
        }

        conflict.targetPath.deleteRecursively()
        conflictStats.recordOverwrite(conflict.targetPath)
        return true
    }

    private fun deleteDirectoryIfEmpty(directory: File) {
        if (!directory.isDirectory) {
            return
        }

        val children = directory.listFiles()
        if (children.isNullOrEmpty()) {
            directory.delete()
        }
    }

    private fun showSuccessNotification(
        sourceModuleName: String,
        newModuleName: String,
        executionResult: SplitExecutionResult,
    ) {
        val actionText = if (executionResult.mergedIntoExistingModule) {
            "已合并到现有模块 $newModuleName"
        } else {
            "已生成模块 $newModuleName"
        }

        val resultText = if (executionResult.movedAnyContent) {
            "模块 $sourceModuleName 已自动引入该依赖"
        } else {
            "未移动任何文件，模块 $sourceModuleName 的依赖未变更"
        }

        val conflictSummary = buildString {
            if (executionResult.overwrittenConflictCount > 0) {
                append("，覆盖 ${executionResult.overwrittenConflictCount} 项冲突")
            }
            if (executionResult.skippedConflictCount > 0) {
                append("，跳过 ${executionResult.skippedConflictCount} 项冲突")
            }
        }

        NotificationGroupManager.getInstance()
            .getNotificationGroup("SplitModule")
            .createNotification(
                "Split Module 成功",
                "$actionText$conflictSummary，$resultText",
                NotificationType.INFORMATION,
            )
            .notify(project)
    }

    private fun showErrorNotification(message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("SplitModule")
            .createNotification(
                "Split Module 失败",
                message,
                NotificationType.ERROR,
            )
            .notify(project)
    }
}

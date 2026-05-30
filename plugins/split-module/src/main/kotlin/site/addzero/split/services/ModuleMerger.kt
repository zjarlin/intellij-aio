package site.addzero.split.services

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtil
import java.io.File

/**
 * 多模块合并服务
 */
class ModuleMerger(private val project: Project) {

    private val logger = Logger.getInstance(ModuleMerger::class.java)

    private data class MergePlan(
        val projectRoot: File,
        val targetModule: VirtualFile,
        val targetModuleDir: File,
        val targetGradlePath: String?,
        val basePackage: String,
        val sourceModules: List<SourceModulePlan>,
        val conflicts: List<PathConflict>,
    )

    private data class SourceModulePlan(
        val moduleRoot: VirtualFile,
        val moduleDir: File,
        val moduleName: String,
        val packageSegment: String,
        val gradlePath: String?,
        val buildFile: File?,
        val preservedRootReadmes: List<File>,
        val moveEntries: List<MoveEntry>,
    )

    private data class MoveEntry(
        val sourceFile: File,
        val targetFile: File,
        val sourceModuleDir: File,
        val oldPackage: String?,
        val newPackage: String?,
    )

    private data class PathConflict(
        val sourcePath: File,
        val targetPath: File,
    )

    private data class SourceRootInfo(
        val rootRelativePath: String,
        val rootName: String,
        val remainingPath: String,
    )

    private data class SymbolMapping(
        val oldPackage: String,
        val newPackage: String,
        val symbolName: String,
    )

    private data class TextBlock(
        val openBraceIndex: Int,
        val closeBraceIndex: Int,
    )

    private data class MergeExecutionResult(
        val mergedModuleCount: Int,
        val movedFileCount: Int,
        val overwrittenConflictCount: Int,
        val skippedConflictCount: Int,
        val dependencyCount: Int,
        val updatedReferenceFileCount: Int,
        val ambiguousSymbolCount: Int,
    )

    private enum class ConflictResolution {
        OVERWRITE,
        SKIP,
    }

    private class MergeStats {
        private val overwrittenTargets = mutableSetOf<String>()
        private val skippedTargets = mutableSetOf<String>()
        private val updatedReferenceFiles = mutableSetOf<String>()
        var movedFileCount: Int = 0
            private set
        var dependencyCount: Int = 0
        var ambiguousSymbolCount: Int = 0

        val overwrittenConflictCount: Int
            get() = overwrittenTargets.size

        val skippedConflictCount: Int
            get() = skippedTargets.size

        val updatedReferenceFileCount: Int
            get() = updatedReferenceFiles.size

        fun recordOverwrite(targetPath: File) {
            overwrittenTargets += targetPath.absolutePath
        }

        fun recordSkip(targetPath: File) {
            skippedTargets += targetPath.absolutePath
        }

        fun recordMove() {
            movedFileCount++
        }

        fun recordUpdatedReference(file: File) {
            updatedReferenceFiles += file.absolutePath
        }
    }

    fun mergeAsync(targetModule: VirtualFile, sourceModules: List<VirtualFile>, basePackage: String) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Merging modules",
            true,
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                mergeInternal(targetModule, sourceModules, basePackage, indicator)
            }
        })
    }

    fun merge(targetModule: VirtualFile, sourceModules: List<VirtualFile>, basePackage: String): Boolean {
        return mergeInternal(targetModule, sourceModules, basePackage, indicator = null)
    }

    private fun mergeInternal(
        targetModule: VirtualFile,
        sourceModules: List<VirtualFile>,
        basePackage: String,
        indicator: ProgressIndicator?,
    ): Boolean {
        return try {
            indicator?.text = "Scanning selected modules..."
            indicator?.fraction = 0.02
            val mergePlan = createMergePlan(targetModule, sourceModules, basePackage, indicator)

            indicator?.text = "Checking conflicts..."
            indicator?.fraction = 0.12
            val conflictResolutions = if (indicator == null) {
                resolveConflicts(mergePlan)
            } else {
                resolveConflictsOnUiThread(mergePlan)
            } ?: return false

            val executionResult = performMerge(mergePlan, conflictResolutions, indicator)

            showSuccessNotification(targetModule.name, executionResult)
            true
        } catch (_: ProcessCanceledException) {
            showCancelNotification()
            false
        } catch (e: Exception) {
            logger.error("Failed to merge modules", e)
            showErrorNotification(e.message ?: "Unknown error")
            false
        }
    }

    private fun createMergePlan(
        targetModule: VirtualFile,
        sourceModules: List<VirtualFile>,
        basePackage: String,
        indicator: ProgressIndicator?,
    ): MergePlan {
        val projectBasePath = project.basePath
            ?: throw IllegalStateException("未找到项目根目录")
        val projectRoot = File(projectBasePath)
        val targetModuleDir = File(targetModule.path)

        if (!isValidPackageName(basePackage)) {
            throw IllegalStateException("基包不是合法 Java/Kotlin package：$basePackage")
        }

        val targetCanonical = targetModuleDir.canonicalFile
        val distinctSources = sourceModules
            .map { it to File(it.path) }
            .distinctBy { (_, dir) -> dir.canonicalPath }
            .filterNot { (_, dir) -> dir.canonicalFile == targetCanonical }

        if (distinctSources.isEmpty()) {
            throw IllegalStateException("至少需要选择一个待合并的子模块")
        }

        val targetGradlePath = targetModuleDir.gradlePathOrNull(projectRoot)
        val sourcePlans = distinctSources.mapIndexed { index, (sourceRoot, sourceDir) ->
            indicator?.checkCanceled()
            indicator?.text = "Scanning module ${sourceRoot.name} (${index + 1}/${distinctSources.size})..."
            indicator?.fraction = 0.02 + (0.08 * (index.toDouble() / distinctSources.size.toDouble()))
            val packageSegment = packageSegmentForModule(sourceRoot.name)
            SourceModulePlan(
                moduleRoot = sourceRoot,
                moduleDir = sourceDir,
                moduleName = sourceRoot.name,
                packageSegment = packageSegment,
                gradlePath = sourceDir.gradlePathOrNull(projectRoot),
                buildFile = sourceDir.findBuildFile(),
                preservedRootReadmes = sourceDir.rootReadmeFiles(),
                moveEntries = createMoveEntries(
                    sourceModuleDir = sourceDir,
                    targetModuleDir = targetModuleDir,
                    moduleName = sourceRoot.name,
                    packageSegment = packageSegment,
                    basePackage = basePackage,
                    indicator = indicator,
                ),
            )
        }

        val duplicateTarget = sourcePlans
            .flatMap { it.moveEntries }
            .groupBy { it.targetFile.canonicalPath }
            .filterValues { it.size > 1 }
            .keys
            .firstOrNull()
        if (duplicateTarget != null) {
            throw IllegalStateException("多个源文件会写入同一路径：$duplicateTarget")
        }

        val conflicts = collectConflicts(targetModuleDir, sourcePlans.flatMap { it.moveEntries })
        return MergePlan(
            projectRoot = projectRoot,
            targetModule = targetModule,
            targetModuleDir = targetModuleDir,
            targetGradlePath = targetGradlePath,
            basePackage = basePackage,
            sourceModules = sourcePlans,
            conflicts = conflicts,
        )
    }

    private fun createMoveEntries(
        sourceModuleDir: File,
        targetModuleDir: File,
        moduleName: String,
        packageSegment: String,
        basePackage: String,
        indicator: ProgressIndicator?,
    ): List<MoveEntry> {
        return sourceModuleDir
            .walkTopDown()
            .onEnter { child -> !child.isExcludedDirectory() }
            .filter { it.isFile }
            .filterNot { it.isBuildDescriptor() }
            .filterNot { it.isRootReadme(sourceModuleDir) }
            .map { sourceFile ->
                indicator?.checkCanceled()
                val sourceRootInfo = sourceFile.sourceRootInfo(sourceModuleDir)
                val oldPackage = sourceFile.readPackageName()
                val newPackage = if (sourceFile.isCodeFile()) {
                    relocatedPackageName(
                        basePackage = basePackage,
                        modulePackageSegment = packageSegment,
                        oldPackage = oldPackage,
                    )
                } else {
                    null
                }
                val targetFile = calculateTargetFile(
                    sourceModuleDir = sourceModuleDir,
                    targetModuleDir = targetModuleDir,
                    moduleName = moduleName,
                    packageSegment = packageSegment,
                    sourceFile = sourceFile,
                    sourceRootInfo = sourceRootInfo,
                    newPackage = newPackage,
                )
                MoveEntry(
                    sourceFile = sourceFile,
                    targetFile = targetFile,
                    sourceModuleDir = sourceModuleDir,
                    oldPackage = oldPackage,
                    newPackage = newPackage,
                )
            }
            .toList()
    }

    private fun calculateTargetFile(
        sourceModuleDir: File,
        targetModuleDir: File,
        moduleName: String,
        packageSegment: String,
        sourceFile: File,
        sourceRootInfo: SourceRootInfo?,
        newPackage: String?,
    ): File {
        val relativePath = sourceFile.relativePathFrom(sourceModuleDir)
        if (sourceRootInfo == null) {
            return File(targetModuleDir, "merged-modules/$moduleName/$relativePath")
        }

        if (sourceFile.isCodeFile() && newPackage != null) {
            val packagePath = newPackage.replace('.', '/')
            return File(targetModuleDir, "${sourceRootInfo.rootRelativePath}/$packagePath/${sourceFile.name}")
        }

        val moduleRelativePath = listOf(packageSegment, sourceRootInfo.remainingPath)
            .filter { it.isNotBlank() }
            .joinToString("/")
        return File(targetModuleDir, "${sourceRootInfo.rootRelativePath}/$moduleRelativePath")
    }

    private fun relocatedPackageName(
        basePackage: String,
        modulePackageSegment: String,
        oldPackage: String?,
    ): String {
        val suffix = when {
            oldPackage.isNullOrBlank() -> ""
            oldPackage == basePackage -> ""
            else -> oldPackage.suffixAfterSharedBasePrefix(basePackage)
        }

        return listOf(basePackage, modulePackageSegment, suffix)
            .filter { it.isNotBlank() }
            .joinToString(".")
    }

    private fun collectConflicts(
        targetModuleDir: File,
        moveEntries: List<MoveEntry>,
    ): List<PathConflict> {
        val conflictsByTargetPath = linkedMapOf<String, PathConflict>()
        moveEntries.forEach { entry ->
            val blockingAncestorConflict = findBlockingAncestorConflict(
                sourcePath = entry.sourceFile,
                targetPath = entry.targetFile,
                targetModuleDir = targetModuleDir,
            )
            if (blockingAncestorConflict != null) {
                conflictsByTargetPath.putIfAbsent(
                    blockingAncestorConflict.targetPath.absolutePath,
                    blockingAncestorConflict,
                )
                return@forEach
            }

            if (entry.targetFile.exists()) {
                conflictsByTargetPath.putIfAbsent(
                    entry.targetFile.absolutePath,
                    PathConflict(sourcePath = entry.sourceFile, targetPath = entry.targetFile),
                )
            }
        }

        return conflictsByTargetPath.values.toList()
    }

    private fun findBlockingAncestorConflict(
        sourcePath: File,
        targetPath: File,
        targetModuleDir: File,
    ): PathConflict? {
        var currentTargetParent = targetPath.parentFile
        var currentSourceParent = sourcePath.parentFile

        while (currentTargetParent != null && currentTargetParent != targetModuleDir.parentFile) {
            if (currentTargetParent.exists() && currentTargetParent.isFile) {
                return PathConflict(
                    sourcePath = currentSourceParent ?: sourcePath,
                    targetPath = currentTargetParent,
                )
            }

            if (currentTargetParent == targetModuleDir) {
                break
            }

            currentTargetParent = currentTargetParent.parentFile
            currentSourceParent = currentSourceParent?.parentFile
        }

        return null
    }

    private fun resolveConflicts(mergePlan: MergePlan): Map<String, ConflictResolution>? {
        if (mergePlan.conflicts.isEmpty()) {
            return emptyMap()
        }

        val conflictResolutions = linkedMapOf<String, ConflictResolution>()
        mergePlan.conflicts.forEach { conflict ->
            val relativeTargetPath = conflict.targetPath
                .relativeTo(mergePlan.targetModuleDir)
                .path
                .replace(File.separatorChar, '/')

            val choice = Messages.showDialog(
                project,
                buildConflictMessage(relativeTargetPath, conflict),
                "Merge Module 冲突",
                arrayOf("覆盖", "跳过", "取消"),
                0,
                Messages.getWarningIcon(),
            )

            when (choice) {
                0 -> conflictResolutions[conflict.targetPath.absolutePath] = ConflictResolution.OVERWRITE
                1 -> conflictResolutions[conflict.targetPath.absolutePath] = ConflictResolution.SKIP
                else -> return null
            }
        }

        return conflictResolutions
    }

    private fun resolveConflictsOnUiThread(mergePlan: MergePlan): Map<String, ConflictResolution>? {
        var result: Map<String, ConflictResolution>? = null
        ApplicationManager.getApplication().invokeAndWait(
            {
                result = resolveConflicts(mergePlan)
            },
            ModalityState.any(),
        )
        return result
    }

    private fun buildConflictMessage(relativeTargetPath: String, conflict: PathConflict): String {
        return buildString {
            appendLine("主模块中已存在冲突项：$relativeTargetPath")
            appendLine("源文件：${conflict.sourcePath.path}")
            appendLine("目标项：${conflict.targetPath.path}")
            append("选择“覆盖”会替换目标项，选择“跳过”会保留源模块中的当前文件。")
        }
    }

    private fun performMerge(
        mergePlan: MergePlan,
        conflictResolutions: Map<String, ConflictResolution>,
        indicator: ProgressIndicator?,
    ): MergeExecutionResult {
        val stats = MergeStats()
        indicator?.text = "Collecting symbol mappings..."
        indicator?.fraction = 0.16
        val symbolMappings = mergePlan.sourceModules
            .flatMap { sourceModulePlan ->
                indicator?.checkCanceled()
                collectSymbolMappings(sourceModulePlan.moveEntries)
            }

        moveSourceFiles(mergePlan, conflictResolutions, stats, indicator)
        indicator?.text = "Fusing Gradle dependencies..."
        indicator?.fraction = 0.62
        stats.dependencyCount = fuseGradleDependencies(mergePlan)
        indicator?.text = "Updating code references..."
        indicator?.fraction = 0.72
        stats.ambiguousSymbolCount = updateCodeReferences(
            projectRoot = mergePlan.projectRoot,
            symbolMappings = symbolMappings,
            stats = stats,
            indicator = indicator,
        )
        indicator?.text = "Updating Gradle project references..."
        indicator?.fraction = 0.9
        updateGradleProjectReferences(mergePlan, stats, indicator)

        indicator?.text = "Removing merged source modules..."
        indicator?.fraction = 0.96
        mergePlan.sourceModules.forEach { sourcePlan ->
            indicator?.checkCanceled()
            val hasSkippedFile = sourcePlan.moveEntries.any { entry ->
                conflictResolutions[entry.targetFile.absolutePath] == ConflictResolution.SKIP
            }
            if (hasSkippedFile) {
                return@forEach
            }

            if (sourcePlan.preservedRootReadmes.any { it.exists() }) {
                deleteModuleContentsExceptRootReadmes(sourcePlan.moduleDir)
            } else {
                sourcePlan.moduleDir.deleteRecursively()
            }
        }

        VfsUtil.markDirtyAndRefresh(
            true,
            true,
            true,
            mergePlan.targetModuleDir,
            *mergePlan.sourceModules.map { it.moduleDir }.toTypedArray(),
        )

        return MergeExecutionResult(
            mergedModuleCount = mergePlan.sourceModules.size,
            movedFileCount = stats.movedFileCount,
            overwrittenConflictCount = stats.overwrittenConflictCount,
            skippedConflictCount = stats.skippedConflictCount,
            dependencyCount = stats.dependencyCount,
            updatedReferenceFileCount = stats.updatedReferenceFileCount,
            ambiguousSymbolCount = stats.ambiguousSymbolCount,
        )
    }

    private fun moveSourceFiles(
        mergePlan: MergePlan,
        conflictResolutions: Map<String, ConflictResolution>,
        stats: MergeStats,
        indicator: ProgressIndicator?,
    ) {
        val total = mergePlan.sourceModules.sumOf { it.moveEntries.size }.coerceAtLeast(1)
        var processed = 0
        mergePlan.sourceModules.forEach { sourcePlan ->
            sourcePlan.moveEntries.forEach { entry ->
                indicator?.checkCanceled()
                indicator?.text = "Moving ${entry.sourceFile.name}..."
                indicator?.fraction = 0.18 + (0.42 * (processed.toDouble() / total.toDouble()))
                if (!prepareTargetForMove(entry, mergePlan.targetModuleDir, conflictResolutions, stats)) {
                    processed++
                    return@forEach
                }

                entry.targetFile.parentFile?.mkdirs()
                if (entry.sourceFile.isCodeFile() && entry.newPackage != null) {
                    val rewritten = entry.sourceFile
                        .readText(Charsets.UTF_8)
                        .replacePackageDeclaration(entry.sourceFile.extension, entry.newPackage)
                    entry.targetFile.writeText(rewritten, Charsets.UTF_8)
                } else {
                    entry.sourceFile.copyTo(entry.targetFile, overwrite = false)
                }
                entry.sourceFile.delete()
                deleteEmptyParents(entry.sourceFile.parentFile, entry.sourceModuleDir)
                stats.recordMove()
                processed++
            }
        }
    }

    private fun prepareTargetForMove(
        entry: MoveEntry,
        targetModuleDir: File,
        conflictResolutions: Map<String, ConflictResolution>,
        stats: MergeStats,
    ): Boolean {
        val blockingAncestorConflict = findBlockingAncestorConflict(
            sourcePath = entry.sourceFile,
            targetPath = entry.targetFile,
            targetModuleDir = targetModuleDir,
        )
        if (blockingAncestorConflict != null) {
            return applyConflictResolution(blockingAncestorConflict, conflictResolutions, stats)
        }

        if (!entry.targetFile.exists()) {
            return true
        }

        val conflict = PathConflict(sourcePath = entry.sourceFile, targetPath = entry.targetFile)
        return applyConflictResolution(conflict, conflictResolutions, stats)
    }

    private fun applyConflictResolution(
        conflict: PathConflict,
        conflictResolutions: Map<String, ConflictResolution>,
        stats: MergeStats,
    ): Boolean {
        val resolution = conflictResolutions[conflict.targetPath.absolutePath]
            ?: throw IllegalStateException("缺少冲突项处理策略：${conflict.targetPath.path}")

        if (resolution == ConflictResolution.SKIP) {
            stats.recordSkip(conflict.targetPath)
            return false
        }

        conflict.targetPath.deleteRecursively()
        stats.recordOverwrite(conflict.targetPath)
        return true
    }

    private fun deleteEmptyParents(start: File?, stopAt: File) {
        var current = start
        while (current != null && current != stopAt && current.isDirectory) {
            val children = current.listFiles()
            if (!children.isNullOrEmpty()) {
                return
            }
            current.delete()
            current = current.parentFile
        }
    }

    private fun collectSymbolMappings(moveEntries: List<MoveEntry>): List<SymbolMapping> {
        return moveEntries
            .filter { it.oldPackage != null && it.newPackage != null }
            .flatMap { entry ->
                entry.sourceFile.collectTopLevelSymbols().map { symbolName ->
                    SymbolMapping(
                        oldPackage = entry.oldPackage.orEmpty(),
                        newPackage = entry.newPackage.orEmpty(),
                        symbolName = symbolName,
                    )
                }
            }
    }

    private fun updateCodeReferences(
        projectRoot: File,
        symbolMappings: List<SymbolMapping>,
        stats: MergeStats,
        indicator: ProgressIndicator?,
    ): Int {
        val mappingsByKey = symbolMappings
            .groupBy { "${it.oldPackage}.${it.symbolName}" }
            .mapValues { (_, mappings) -> mappings.map { it.newPackage }.distinct() }
        val ambiguousKeyCount = mappingsByKey.count { (_, newPackages) -> newPackages.size > 1 }
        val uniqueMappings = symbolMappings
            .filter { mapping -> mappingsByKey["${mapping.oldPackage}.${mapping.symbolName}"]?.size == 1 }
            .distinctBy { "${it.oldPackage}.${it.symbolName}.${it.newPackage}" }
            .sortedWith(compareByDescending<SymbolMapping> { it.oldPackage.length }.thenByDescending { it.symbolName.length })
        val wildcardPackages = uniqueMappings
            .groupBy { it.oldPackage }
            .mapValues { (_, mappings) -> mappings.map { it.newPackage }.distinct().sorted() }

        val codeFiles = projectRoot
            .walkTopDown()
            .onEnter { file -> !file.isExcludedDirectory() }
            .filter { it.isFile && it.isCodeFile() }
            .toList()

        val total = codeFiles.size.coerceAtLeast(1)
        codeFiles.forEachIndexed { index, file ->
                indicator?.checkCanceled()
                indicator?.text = "Updating code references (${index + 1}/${codeFiles.size})..."
                indicator?.fraction = 0.72 + (0.16 * (index.toDouble() / total.toDouble()))
                val original = file.readText(Charsets.UTF_8)
                var updated = original

                wildcardPackages.forEach { (oldPackage, newPackages) ->
                    val wildcardImport = Regex("""(?m)^(\s*)import\s+${Regex.escape(oldPackage)}\.\*\s*$""")
                    updated = wildcardImport.replace(updated) { match ->
                        val indent = match.groupValues[1]
                        newPackages.joinToString("\n") { newPackage -> "${indent}import $newPackage.*" }
                    }
                }

                uniqueMappings.forEach { mapping ->
                    val oldQualifiedName = "${mapping.oldPackage}.${mapping.symbolName}"
                    val newQualifiedName = "${mapping.newPackage}.${mapping.symbolName}"
                    updated = updated.replaceQualifiedName(oldQualifiedName, newQualifiedName)
                }

                if (updated != original) {
                    file.writeText(updated, Charsets.UTF_8)
                    stats.recordUpdatedReference(file)
                }
        }

        return ambiguousKeyCount
    }

    private fun fuseGradleDependencies(mergePlan: MergePlan): Int {
        val targetBuildFile = mergePlan.targetModuleDir.findBuildFile() ?: return 0
        if (!targetBuildFile.name.startsWith("build.gradle")) {
            return 0
        }

        val sourceGradlePaths = mergePlan.sourceModules.mapNotNull { it.gradlePath }.toSet()
        val sourceModuleNames = mergePlan.sourceModules.map { it.moduleName }.toSet()
        val targetGradlePath = mergePlan.targetGradlePath
        var targetContent = targetBuildFile.readText(Charsets.UTF_8)
        targetContent = removeMergedModuleReferences(
            content = targetContent,
            sourceModuleNames = sourceModuleNames,
            sourceGradlePaths = sourceGradlePaths,
            targetGradlePath = targetGradlePath,
        )

        val existingStatements = extractDependencyStatements(targetContent)
            .map { it.normalizedGradleStatement() }
            .toMutableSet()
        val addedStatements = linkedSetOf<String>()
        mergePlan.sourceModules
            .mapNotNull { it.buildFile }
            .filter { it.name.startsWith("build.gradle") }
            .forEach { sourceBuildFile ->
                extractDependencyStatements(sourceBuildFile.readText(Charsets.UTF_8))
                    .filterNot { statement ->
                        statement.referencesAnyProjectPath(sourceGradlePaths) ||
                            (targetGradlePath != null && statement.referencesProjectPath(targetGradlePath))
                    }
                    .forEach { statement ->
                        val normalized = statement.normalizedGradleStatement()
                        if (existingStatements.add(normalized)) {
                            addedStatements += statement
                        }
                    }
            }

        if (addedStatements.isNotEmpty()) {
            targetContent = insertDependencyStatements(targetContent, addedStatements.toList())
        }

        targetContent = dedupeGradleProjectDependencyLines(
            content = targetContent,
            selfGradlePath = targetGradlePath,
        )
        targetBuildFile.writeText(targetContent, Charsets.UTF_8)
        return addedStatements.size
    }

    private fun updateGradleProjectReferences(
        mergePlan: MergePlan,
        stats: MergeStats,
        indicator: ProgressIndicator?,
    ) {
        val targetGradlePath = mergePlan.targetGradlePath ?: return
        val sourceGradlePaths = mergePlan.sourceModules.mapNotNull { it.gradlePath }.toSet()
        if (sourceGradlePaths.isEmpty()) {
            return
        }

        val deletedSourceDirs = mergePlan.sourceModules.map { it.moduleDir.canonicalFile }
        val buildFiles = mergePlan.projectRoot
            .walkTopDown()
            .onEnter { file -> !file.isExcludedDirectory() && deletedSourceDirs.none { file.canonicalFile == it } }
            .filter { it.isFile && (it.name == "build.gradle.kts" || it.name == "build.gradle") }
            .toList()

        val total = buildFiles.size.coerceAtLeast(1)
        buildFiles.forEachIndexed { index, buildFile ->
                indicator?.checkCanceled()
                indicator?.text = "Updating Gradle references (${index + 1}/${buildFiles.size})..."
                indicator?.fraction = 0.9 + (0.05 * (index.toDouble() / total.toDouble()))
                var content = buildFile.readText(Charsets.UTF_8)
                val original = content
                sourceGradlePaths.forEach { sourceGradlePath ->
                    content = content.replaceProjectPath(sourceGradlePath, targetGradlePath)
                }

                if (buildFile.canonicalFile == mergePlan.targetModuleDir.findBuildFile()?.canonicalFile) {
                    content = removeMergedModuleReferences(
                        content = content,
                        sourceModuleNames = mergePlan.sourceModules.map { it.moduleName }.toSet(),
                        sourceGradlePaths = sourceGradlePaths,
                        targetGradlePath = targetGradlePath,
                    )
                }

                content = dedupeGradleProjectDependencyLines(content, selfGradlePath = null)
                if (content != original) {
                    buildFile.writeText(content, Charsets.UTF_8)
                    stats.recordUpdatedReference(buildFile)
                }
        }
    }

    private fun removeMergedModuleReferences(
        content: String,
        sourceModuleNames: Set<String>,
        sourceGradlePaths: Set<String>,
        targetGradlePath: String?,
    ): String {
        val pathsToRemove = sourceGradlePaths + listOfNotNull(targetGradlePath)
        return content
            .lines()
            .filterNot { line ->
                val trimmed = line.trim()
                sourceModuleNames.any { moduleName ->
                    trimmed == "\"$moduleName\"," ||
                        trimmed == "\"$moduleName\"" ||
                        trimmed == "'$moduleName'," ||
                        trimmed == "'$moduleName'"
                } || pathsToRemove.any { path -> line.referencesProjectPath(path) }
            }
            .joinToString("\n")
            .withTrailingNewlineLike(content)
    }

    private fun extractDependencyStatements(content: String): List<String> {
        return findBlocks(content, Regex("""(?<![\w])dependencies\s*\{"""))
            .flatMap { block ->
                val blockContent = content.substring(block.openBraceIndex + 1, block.closeBraceIndex)
                collectDependencyStatements(blockContent)
            }
    }

    private fun collectDependencyStatements(blockContent: String): List<String> {
        val statements = mutableListOf<String>()
        val current = mutableListOf<String>()
        var parenBalance = 0
        var braceBalance = 0

        fun flushCurrent() {
            if (current.isNotEmpty()) {
                statements += current.joinToString("\n")
                current.clear()
                parenBalance = 0
                braceBalance = 0
            }
        }

        blockContent.lines().forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (current.isEmpty()) {
                if (!trimmed.looksLikeDependencyStatement()) {
                    return@forEach
                }
            }

            current += trimmed
            parenBalance += trimmed.count { it == '(' } - trimmed.count { it == ')' }
            braceBalance += trimmed.count { it == '{' } - trimmed.count { it == '}' }
            if (parenBalance <= 0 && braceBalance <= 0) {
                flushCurrent()
            }
        }

        flushCurrent()
        return statements
    }

    private fun insertDependencyStatements(content: String, statements: List<String>): String {
        val targetBlock = findBlocks(content, Regex("""commonMain\s*\.\s*dependencies\s*\{""")).firstOrNull()
            ?: findBlocks(content, Regex("""(?<![\w])dependencies\s*\{""")).firstOrNull()

        if (targetBlock == null) {
            return content.trimEnd() + "\n\ndependencies {\n" +
                statements.joinToString("\n") { statement -> statement.reindent("    ") } +
                "\n}\n"
        }

        val statementIndent = content.lineIndentAt(targetBlock.openBraceIndex) + "    "
        val insertion = "\n" + statements.joinToString("\n") { statement ->
            statement.reindent(statementIndent)
        }
        return content.substring(0, targetBlock.closeBraceIndex) +
            insertion +
            content.substring(targetBlock.closeBraceIndex)
    }

    private fun findBlocks(content: String, startRegex: Regex): List<TextBlock> {
        return startRegex.findAll(content)
            .mapNotNull { match ->
                val openBraceIndex = content.indexOf('{', startIndex = match.range.first)
                if (openBraceIndex == -1) {
                    null
                } else {
                    val closeBraceIndex = findMatchingBrace(content, openBraceIndex)
                    closeBraceIndex?.let {
                        TextBlock(openBraceIndex = openBraceIndex, closeBraceIndex = it)
                    }
                }
            }
            .toList()
    }

    private fun findMatchingBrace(content: String, openBraceIndex: Int): Int? {
        var depth = 0
        var inString: Char? = null
        var escaped = false
        for (index in openBraceIndex until content.length) {
            val char = content[index]
            if (inString != null) {
                if (escaped) {
                    escaped = false
                } else if (char == '\\') {
                    escaped = true
                } else if (char == inString) {
                    inString = null
                }
                continue
            }

            when (char) {
                '"', '\'' -> inString = char
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return index
                    }
                }
            }
        }

        return null
    }

    private fun dedupeGradleProjectDependencyLines(
        content: String,
        selfGradlePath: String?,
    ): String {
        val seenProjectDependencies = mutableSetOf<String>()
        return content
            .lines()
            .filterNot { line ->
                val trimmed = line.trim()
                val projectPath = trimmed.projectPathReference()
                if (projectPath == null) {
                    false
                } else if (projectPath == selfGradlePath) {
                    true
                } else {
                    !seenProjectDependencies.add(trimmed.normalizedGradleStatement())
                }
            }
            .joinToString("\n")
            .withTrailingNewlineLike(content)
    }

    private fun File.sourceRootInfo(moduleDir: File): SourceRootInfo? {
        val segments = relativePathFrom(moduleDir)
            .split("/")
            .filter { it.isNotBlank() }
        if (segments.size < 3) {
            return null
        }

        val sourceRootIndex = segments.indexOfFirst { it == "src" }
        if (sourceRootIndex == -1 || sourceRootIndex + 2 >= segments.size) {
            return null
        }

        val rootName = segments[sourceRootIndex + 2]
        if (rootName !in sourceRootNames) {
            return null
        }

        val rootSegments = segments.take(sourceRootIndex + 3)
        val remainingSegments = segments.drop(sourceRootIndex + 3)
        return SourceRootInfo(
            rootRelativePath = rootSegments.joinToString("/"),
            rootName = rootName,
            remainingPath = remainingSegments.joinToString("/"),
        )
    }

    private fun File.readPackageName(): String? {
        if (!isCodeFile()) {
            return null
        }

        val content = readText(Charsets.UTF_8)
        return when (extension) {
            "kt", "kts" -> kotlinPackageRegex.find(content)?.groupValues?.get(1)
            "java" -> javaPackageRegex.find(content)?.groupValues?.get(1)
            else -> null
        }
    }

    private fun String.replacePackageDeclaration(extension: String, newPackage: String): String {
        return when (extension) {
            "kt", "kts" -> {
                if (kotlinPackageRegex.containsMatchIn(this)) {
                    replaceFirst(kotlinPackageRegex, "package $newPackage")
                } else {
                    "package $newPackage\n\n$this"
                }
            }

            "java" -> {
                if (javaPackageRegex.containsMatchIn(this)) {
                    replaceFirst(javaPackageRegex, "package $newPackage;")
                } else {
                    "package $newPackage;\n\n$this"
                }
            }

            else -> this
        }
    }

    private fun File.collectTopLevelSymbols(): Set<String> {
        if (!isCodeFile()) {
            return emptySet()
        }

        val content = readText(Charsets.UTF_8)
        val symbols = linkedSetOf<String>()
        kotlinTopLevelSymbolRegex.findAll(content).forEach { match ->
            symbols += match.groupValues[2]
        }
        javaTopLevelSymbolRegex.findAll(content).forEach { match ->
            symbols += match.groupValues[2]
        }
        return symbols
    }

    private fun File.findBuildFile(): File? {
        return listOf("build.gradle.kts", "build.gradle", "pom.xml")
            .map { File(this, it) }
            .firstOrNull { it.isFile }
    }

    private fun File.rootReadmeFiles(): List<File> {
        return listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.startsWith("README", ignoreCase = true) }
            .sortedBy { it.name }
    }

    private fun File.isRootReadme(moduleDir: File): Boolean {
        return parentFile?.canonicalFile == moduleDir.canonicalFile &&
            name.startsWith("README", ignoreCase = true)
    }

    private fun deleteModuleContentsExceptRootReadmes(moduleDir: File) {
        moduleDir
            .listFiles()
            .orEmpty()
            .filterNot { it.isFile && it.name.startsWith("README", ignoreCase = true) }
            .forEach { it.deleteRecursively() }
    }

    private fun File.gradlePathOrNull(projectRoot: File): String? {
        if (!startsWith(projectRoot)) {
            return null
        }

        return PathCalculator.calculateGradlePath(projectRoot, this)
    }

    private fun File.relativePathFrom(base: File): String {
        return relativeTo(base).path.replace(File.separatorChar, '/')
    }

    private fun File.isCodeFile(): Boolean {
        return extension in codeFileExtensions
    }

    private fun File.isBuildDescriptor(): Boolean {
        return name in buildDescriptorNames
    }

    private fun File.isExcludedDirectory(): Boolean {
        return isDirectory && name in excludedDirectoryNames
    }

    private fun String.replaceQualifiedName(oldQualifiedName: String, newQualifiedName: String): String {
        if (!contains(oldQualifiedName)) {
            return this
        }

        val result = StringBuilder(length)
        var searchStart = 0
        while (true) {
            val index = indexOf(oldQualifiedName, startIndex = searchStart)
            if (index == -1) {
                result.append(this, searchStart, length)
                break
            }

            if (isQualifiedNameBoundary(index, index + oldQualifiedName.length)) {
                result.append(this, searchStart, index)
                result.append(newQualifiedName)
            } else {
                result.append(this, searchStart, index + oldQualifiedName.length)
            }
            searchStart = index + oldQualifiedName.length
        }

        return result.toString()
    }

    private fun String.isQualifiedNameBoundary(startIndex: Int, endIndex: Int): Boolean {
        val before = getOrNull(startIndex - 1)
        val after = getOrNull(endIndex)
        val beforeOk = before == null || (!before.isJavaIdentifierPart() && before != '.')
        val afterOk = after == null || !after.isJavaIdentifierPart()
        return beforeOk && afterOk
    }

    private fun String.suffixAfterSharedBasePrefix(basePackage: String): String {
        val oldSegments = split('.').filter { it.isNotBlank() }
        val baseSegments = basePackage.split('.').filter { it.isNotBlank() }
        val commonLength = oldSegments
            .zip(baseSegments)
            .takeWhile { (oldSegment, baseSegment) -> oldSegment == baseSegment }
            .size

        return oldSegments
            .drop(commonLength)
            .joinToString(".")
    }

    private fun String.replaceProjectPath(sourceGradlePath: String, targetGradlePath: String): String {
        return replace("project(\"$sourceGradlePath\")", "project(\"$targetGradlePath\")")
            .replace("project('$sourceGradlePath')", "project('$targetGradlePath')")
            .replace(
                Regex("""project\(\s*path\s*=\s*"${Regex.escape(sourceGradlePath)}"\s*\)"""),
                "project(path = \"$targetGradlePath\")",
            )
            .replace(
                Regex("""project\(\s*path\s*=\s*'${Regex.escape(sourceGradlePath)}'\s*\)"""),
                "project(path = '$targetGradlePath')",
            )
    }

    private fun String.referencesAnyProjectPath(projectPaths: Set<String>): Boolean {
        return projectPaths.any { referencesProjectPath(it) }
    }

    private fun String.referencesProjectPath(projectPath: String): Boolean {
        return contains("project(\"$projectPath\")") ||
            contains("project('$projectPath')") ||
            contains("project(path = \"$projectPath\")") ||
            contains("project(path = '$projectPath')")
    }

    private fun String.projectPathReference(): String? {
        return projectPathReferenceRegex.find(this)?.groupValues?.get(1)
    }

    private fun String.looksLikeDependencyStatement(): Boolean {
        if (isBlank() || startsWith("//") || startsWith("/*") || startsWith("*")) {
            return false
        }

        val configuration = dependencyConfigurationRegex.find(this)?.groupValues?.get(1)
        return configuration in dependencyConfigurations
    }

    private fun String.normalizedGradleStatement(): String {
        return lines()
            .joinToString(" ") { it.trim().trimEnd(',') }
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun String.reindent(indent: String): String {
        return lines().joinToString("\n") { line ->
            if (line.isBlank()) {
                line
            } else {
                indent + line.trim()
            }
        }
    }

    private fun String.lineIndentAt(index: Int): String {
        val lineStart = lastIndexOf('\n', startIndex = index).let { if (it == -1) 0 else it + 1 }
        return substring(lineStart, index).takeWhile { it == ' ' || it == '\t' }
    }

    private fun String.withTrailingNewlineLike(original: String): String {
        return if (original.endsWith("\n") && !endsWith("\n")) {
            "$this\n"
        } else {
            this
        }
    }

    private fun showSuccessNotification(targetModuleName: String, executionResult: MergeExecutionResult) {
        val conflictSummary = buildString {
            if (executionResult.overwrittenConflictCount > 0) {
                append("，覆盖 ${executionResult.overwrittenConflictCount} 项冲突")
            }
            if (executionResult.skippedConflictCount > 0) {
                append("，跳过 ${executionResult.skippedConflictCount} 项冲突")
            }
            if (executionResult.ambiguousSymbolCount > 0) {
                append("，${executionResult.ambiguousSymbolCount} 个重名符号未自动改引用")
            }
        }

        NotificationGroupManager.getInstance()
            .getNotificationGroup("SplitModule")
            .createNotification(
                "Merge Module 成功",
                "已合并 ${executionResult.mergedModuleCount} 个模块到 $targetModuleName，移动 ${executionResult.movedFileCount} 个文件，新增 ${executionResult.dependencyCount} 条依赖，更新 ${executionResult.updatedReferenceFileCount} 个引用文件$conflictSummary",
                NotificationType.INFORMATION,
            )
            .notify(project)
    }

    private fun showErrorNotification(message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("SplitModule")
            .createNotification(
                "Merge Module 失败",
                message,
                NotificationType.ERROR,
            )
            .notify(project)
    }

    private fun showCancelNotification() {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("SplitModule")
            .createNotification(
                "Merge Module 已取消",
                "合并任务已取消，已完成的文件系统变更不会自动回滚。",
                NotificationType.INFORMATION,
            )
            .notify(project)
    }

    companion object {
        private val sourceRootNames = setOf("kotlin", "java", "resources")
        private val codeFileExtensions = setOf("kt", "kts", "java")
        private val buildDescriptorNames = setOf("build.gradle.kts", "build.gradle", "pom.xml")
        private val excludedDirectoryNames = setOf(".git", ".gradle", ".idea", "build", "out")
        private val kotlinPackageRegex = Regex("""(?m)^\s*package\s+([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*)\s*$""")
        private val javaPackageRegex = Regex("""(?m)^\s*package\s+([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*)\s*;""")
        private val kotlinTopLevelSymbolRegex = Regex(
            """(?m)^(?:@\w+(?:\([^)]*\))?\s*)*(?:public|internal|private|protected|expect|actual|data|sealed|open|abstract|enum|annotation|value|inline|tailrec|operator|infix|suspend|const|external|lateinit|override|\s)*\b(class|interface|object|fun|val|var|typealias)\s+([A-Za-z_][A-Za-z0-9_]*)"""
        )
        private val javaTopLevelSymbolRegex = Regex("""(?m)^\s*(?:public|private|protected|abstract|final|sealed|non-sealed|static|\s)*\b(class|interface|enum|record)\s+([A-Za-z_][A-Za-z0-9_]*)""")
        private val dependencyConfigurationRegex = Regex("""^([A-Za-z_][A-Za-z0-9_]*)\s*(?:\(|\s+)""")
        private val dependencyConfigurations = setOf(
            "api",
            "implementation",
            "compileOnly",
            "runtimeOnly",
            "compile",
            "runtime",
            "testImplementation",
            "testRuntimeOnly",
            "testCompileOnly",
            "androidTestImplementation",
            "debugImplementation",
            "releaseImplementation",
            "kapt",
            "ksp",
        )
        private val projectPathReferenceRegex = Regex("""project\(\s*(?:path\s*=\s*)?["']([^"']+)["']\s*\)""")

        fun inferCommonBasePackage(
            moduleRoots: List<VirtualFile>,
            indicator: ProgressIndicator? = null,
        ): String {
            val packages = moduleRoots
                .flatMapIndexed { index, root ->
                    indicator?.checkCanceled()
                    indicator?.text = "Scanning packages in ${root.name} (${index + 1}/${moduleRoots.size})..."
                    indicator?.fraction = index.toDouble() / moduleRoots.size.toDouble().coerceAtLeast(1.0)
                    File(root.path)
                        .walkTopDown()
                        .onEnter { file ->
                            indicator?.checkCanceled()
                            !(file.isDirectory && file.name in excludedDirectoryNames)
                        }
                        .filter { it.isFile && it.extension in codeFileExtensions }
                        .mapNotNull { file ->
                            indicator?.checkCanceled()
                            val content = file.readText(Charsets.UTF_8)
                            kotlinPackageRegex.find(content)?.groupValues?.get(1)
                                ?: javaPackageRegex.find(content)?.groupValues?.get(1)
                        }
                        .toList()
                }
                .distinct()

            if (packages.isEmpty()) {
                return "site.addzero"
            }

            val commonSegments = packages
                .map { it.split(".") }
                .reduce { common, current ->
                    common.zip(current)
                        .takeWhile { (left, right) -> left == right }
                        .map { it.first }
                }

            return commonSegments.joinToString(".").ifBlank { packages.first().substringBeforeLast('.', "site.addzero") }
        }

        fun packageSegmentForModule(moduleName: String): String {
            val sanitized = moduleName
                .lowercase()
                .replace(Regex("[^a-z0-9_]"), "_")
                .replace(Regex("_+"), "_")
                .trim('_')
                .ifBlank { "module" }

            return if (sanitized.first().isDigit()) {
                "m_$sanitized"
            } else {
                sanitized
            }
        }

        fun isValidPackageName(packageName: String): Boolean {
            return packageName
                .split(".")
                .all { segment ->
                    segment.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) &&
                        segment !in javaKeywords
                }
        }

        private val javaKeywords = setOf(
            "abstract",
            "assert",
            "boolean",
            "break",
            "byte",
            "case",
            "catch",
            "char",
            "class",
            "const",
            "continue",
            "default",
            "do",
            "double",
            "else",
            "enum",
            "extends",
            "final",
            "finally",
            "float",
            "for",
            "goto",
            "if",
            "implements",
            "import",
            "instanceof",
            "int",
            "interface",
            "long",
            "native",
            "new",
            "package",
            "private",
            "protected",
            "public",
            "return",
            "short",
            "static",
            "strictfp",
            "super",
            "switch",
            "synchronized",
            "this",
            "throw",
            "throws",
            "transient",
            "try",
            "void",
            "volatile",
            "while",
        )
    }
}

package site.addzero.split.services

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

class CommonFolderPrefixCleaner(private val project: Project) {

    data class PrefixMatch(
        val prefix: String,
        val occurrenceCount: Int,
        val score: Int,
    )

    data class PackageMapping(
        val oldPackage: String,
        val newPackage: String,
    )

    data class RenameEntry(
        val folder: VirtualFile,
        val parentPath: String,
        val oldName: String,
        val newName: String,
        val oldPackage: String?,
        val newPackage: String?,
    )

    data class GroupPlan(
        val parent: VirtualFile,
        val parentPath: String,
        val prefix: String,
        val occurrenceCount: Int,
        val renameEntries: List<RenameEntry>,
    )

    data class CleanupPlan(
        val groupPlans: List<GroupPlan>,
    ) {
        val renameEntries: List<RenameEntry> = groupPlans.flatMap { it.renameEntries }
        val packageMappings: List<PackageMapping> = renameEntries
            .mapNotNull { entry ->
                val oldPackage = entry.oldPackage
                val newPackage = entry.newPackage
                if (oldPackage != null && newPackage != null && oldPackage != newPackage) {
                    PackageMapping(oldPackage, newPackage)
                } else {
                    null
                }
            }
            .distinct()
            .sortedByDescending { it.oldPackage.length }

        fun prefixSummary(): String {
            return groupPlans
                .map { it.prefix }
                .distinct()
                .joinToString(", ")
        }
    }

    data class CleanupResult(
        val renamedFolderCount: Int,
        val updatedCodeFileCount: Int,
    )

    private data class CandidateGroup(
        val parent: VirtualFile,
        val folders: List<VirtualFile>,
    )

    fun createPlan(selectedDirectories: List<VirtualFile>): CleanupPlan {
        val groupPlans = resolveCandidateGroups(selectedDirectories)
            .mapNotNull { group -> createGroupPlan(group) }

        if (groupPlans.isEmpty()) {
            throw IllegalStateException("No repeated removable folder prefix was found.")
        }

        return CleanupPlan(groupPlans)
    }

    fun apply(plan: CleanupPlan, indicator: ProgressIndicator?): CleanupResult {
        validateTargetsStillAvailable(plan.renameEntries)
        renameFolders(plan.renameEntries)

        indicator?.text = "Updating package references..."
        val updatedCodeFiles = updateCodeReferences(plan.packageMappings, indicator)
        VfsUtil.markDirtyAndRefresh(
            true,
            true,
            true,
            *plan.groupPlans.map { it.parent }.toTypedArray(),
        )

        return CleanupResult(
            renamedFolderCount = plan.renameEntries.size,
            updatedCodeFileCount = updatedCodeFiles,
        )
    }

    fun notifySuccess(title: String, message: String) {
        notify(title, message, NotificationType.INFORMATION)
    }

    fun notifyError(title: String, message: String) {
        notify(title, message, NotificationType.ERROR)
    }

    private fun resolveCandidateGroups(selectedDirectories: List<VirtualFile>): List<CandidateGroup> {
        val directories = selectedDirectories
            .filter { it.isDirectory }
            .distinctBy { it.path }
        if (directories.isEmpty()) {
            return emptyList()
        }

        if (directories.size == 1) {
            val parent = directories.single()
            return listOf(
                CandidateGroup(
                    parent = parent,
                    folders = parent.children.filter { it.isDirectory && !it.isExcludedDirectory() },
                ),
            )
        }

        val commonParent = directories.first().parent
        if (commonParent != null && directories.all { it.parent?.path == commonParent.path }) {
            return listOf(CandidateGroup(commonParent, directories))
        }

        return directories.mapNotNull { parent ->
            val childDirectories = parent.children.filter { it.isDirectory && !it.isExcludedDirectory() }
            if (childDirectories.size >= 2) {
                CandidateGroup(parent, childDirectories)
            } else {
                null
            }
        }
    }

    private fun createGroupPlan(group: CandidateGroup): GroupPlan? {
        val prefixMatch = inferMostRepeatedPrefix(group.folders.map { it.name }) ?: return null
        val entries = group.folders
            .filter { it.name.startsWith(prefixMatch.prefix) }
            .mapNotNull { folder ->
                val newName = folder.name.removePrefix(prefixMatch.prefix)
                if (newName.isBlank()) {
                    return@mapNotNull null
                }

                val oldPackage = packagePrefixForDirectory(File(folder.path))
                val newPackage = packagePrefixForDirectory(File(group.parent.path, newName))
                if (oldPackage != null && newPackage == null) {
                    throw IllegalStateException(
                        "Removing '${prefixMatch.prefix}' from '${folder.name}' creates an invalid package segment: '$newName'.",
                    )
                }

                RenameEntry(
                    folder = folder,
                    parentPath = group.parent.path,
                    oldName = folder.name,
                    newName = newName,
                    oldPackage = oldPackage,
                    newPackage = newPackage,
                )
            }

        if (entries.size < MIN_PREFIX_OCCURRENCES) {
            return null
        }

        validateRenameConflicts(entries)
        return GroupPlan(
            parent = group.parent,
            parentPath = group.parent.path,
            prefix = prefixMatch.prefix,
            occurrenceCount = prefixMatch.occurrenceCount,
            renameEntries = entries,
        )
    }

    private fun validateRenameConflicts(entries: List<RenameEntry>) {
        val duplicateTarget = entries
            .groupBy { "${it.parentPath}/${it.newName}" }
            .filterValues { it.size > 1 }
            .keys
            .firstOrNull()
        if (duplicateTarget != null) {
            throw IllegalStateException("Multiple folders would be renamed to the same path: $duplicateTarget")
        }

        val existingTarget = entries
            .firstOrNull { File(it.parentPath, it.newName).exists() }
        if (existingTarget != null) {
            throw IllegalStateException(
                "Cannot rename '${existingTarget.oldName}' because target folder already exists: " +
                    File(existingTarget.parentPath, existingTarget.newName).path,
            )
        }
    }

    private fun validateTargetsStillAvailable(entries: List<RenameEntry>) {
        entries.firstOrNull { File(it.parentPath, it.newName).exists() }?.let { entry ->
            throw IllegalStateException("Target folder already exists: ${File(entry.parentPath, entry.newName).path}")
        }
    }

    private fun renameFolders(entries: List<RenameEntry>) {
        WriteCommandAction.runWriteCommandAction(project, "Remove Common Folder Prefix", null, Runnable {
            entries.forEach { entry ->
                entry.folder.rename(this, entry.newName)
            }
        })
    }

    private fun updateCodeReferences(
        packageMappings: List<PackageMapping>,
        indicator: ProgressIndicator?,
    ): Int {
        if (packageMappings.isEmpty()) {
            return 0
        }

        val projectRoot = project.basePath?.let(::File) ?: return 0
        val codeFiles = projectRoot
            .walkTopDown()
            .onEnter { file -> !file.isExcludedDirectory() }
            .filter { it.isFile && it.extension in codeFileExtensions }
            .toList()

        var updatedCount = 0
        val total = codeFiles.size.coerceAtLeast(1)
        codeFiles.forEachIndexed { index, file ->
            indicator?.checkCanceled()
            indicator?.text = "Updating package references (${index + 1}/${codeFiles.size})..."
            indicator?.fraction = index.toDouble() / total.toDouble()

            val original = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return@forEachIndexed
            val updated = rewritePackageReferences(original, packageMappings)
            if (updated != original) {
                file.writeText(updated, Charsets.UTF_8)
                updatedCount++
            }
        }

        return updatedCount
    }

    private fun notify(title: String, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("IdeKit Notifications")
            .createNotification(title, message, type)
            .notify(project)
    }

    private fun VirtualFile.isExcludedDirectory(): Boolean {
        return isDirectory && name in excludedDirectoryNames
    }

    private fun File.isExcludedDirectory(): Boolean {
        return isDirectory && name in excludedDirectoryNames
    }

    companion object {
        private const val MIN_PREFIX_OCCURRENCES = 2
        private val prefixDelimiters = setOf('_', '-')
        private val sourceRootNames = setOf("kotlin", "java")
        private val codeFileExtensions = setOf("kt", "kts", "java")
        private val excludedDirectoryNames = setOf(".git", ".gradle", ".idea", ".kotlin", "build", "out")

        fun inferMostRepeatedPrefix(folderNames: Collection<String>): PrefixMatch? {
            val prefixCounts = linkedMapOf<String, Int>()
            folderNames
                .distinct()
                .forEach { name ->
                    removablePrefixCandidates(name).forEach { prefix ->
                        prefixCounts[prefix] = prefixCounts.getOrDefault(prefix, 0) + 1
                    }
                }

            return prefixCounts
                .filterValues { it >= MIN_PREFIX_OCCURRENCES }
                .map { (prefix, count) ->
                    PrefixMatch(
                        prefix = prefix,
                        occurrenceCount = count,
                        score = prefix.length * count,
                    )
                }
                .maxWithOrNull(
                    compareBy<PrefixMatch> { it.score }
                        .thenBy { it.occurrenceCount }
                        .thenBy { it.prefix.length }
                        .thenByDescending { it.prefix },
                )
        }

        fun removablePrefixCandidates(folderName: String): List<String> {
            return folderName.indices
                .filter { index -> folderName[index] in prefixDelimiters }
                .map { index -> folderName.substring(0, index + 1) }
                .filter { prefix ->
                    prefix.length > 1 && folderName.removePrefix(prefix).isNotBlank()
                }
        }

        fun packagePrefixForDirectory(directory: File): String? {
            val sourceRoot = directory.findSourceRoot() ?: return null
            val relativePath = directory.relativeTo(sourceRoot).path.replace(File.separatorChar, '/')
            if (relativePath.isBlank()) {
                return null
            }

            val segments = relativePath
                .split("/")
                .filter { it.isNotBlank() }
            if (segments.isEmpty() || segments.any { !it.isValidPackageSegment() }) {
                return null
            }

            return segments.joinToString(".")
        }

        fun rewritePackageReferences(content: String, mappings: List<PackageMapping>): String {
            return mappings
                .sortedByDescending { it.oldPackage.length }
                .fold(content) { current, mapping ->
                    current.replacePackagePrefix(mapping.oldPackage, mapping.newPackage)
                }
        }

        private fun File.findSourceRoot(): File? {
            var current: File? = this
            while (current != null) {
                val parent = current.parentFile
                val grandParent = parent?.parentFile
                if (current.name in sourceRootNames && grandParent?.name == "src") {
                    return current
                }
                current = parent
            }
            return null
        }

        private fun String.isValidPackageSegment(): Boolean {
            return matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) && this !in javaKeywords
        }

        private fun String.replacePackagePrefix(oldPackage: String, newPackage: String): String {
            if (!contains(oldPackage)) {
                return this
            }

            val result = StringBuilder(length)
            var searchStart = 0
            while (true) {
                val index = indexOf(oldPackage, startIndex = searchStart)
                if (index == -1) {
                    result.append(this, searchStart, length)
                    break
                }

                val endIndex = index + oldPackage.length
                if (isPackagePrefixBoundary(index, endIndex)) {
                    result.append(this, searchStart, index)
                    result.append(newPackage)
                } else {
                    result.append(this, searchStart, endIndex)
                }
                searchStart = endIndex
            }
            return result.toString()
        }

        private fun String.isPackagePrefixBoundary(startIndex: Int, endIndex: Int): Boolean {
            val before = getOrNull(startIndex - 1)
            val after = getOrNull(endIndex)
            val beforeOk = before == null || (!before.isJavaIdentifierPart() && before != '.')
            val afterOk = after == null || after == '.' || !after.isJavaIdentifierPart()
            return beforeOk && afterOk
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

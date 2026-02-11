package site.addzero.gradle.buddy.migration.versioncatalog

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import site.addzero.gradle.buddy.util.StringUtils.toCamelCaseByDelimiters
import site.addzero.gradle.buddy.util.StringUtils.toKebabCase
import site.addzero.gradle.buddy.settings.GradleBuddySettingsService
import java.io.File

/**
 * 批量迁移依赖到 Version Catalog
 *
 * 扫描项目中所有 .gradle.kts 文件，提取硬编码依赖，
 * 生成/更新 gradle/libs.versions.toml，并替换为 catalog 引用
 */
class MigrateToVersionCatalogAction : AnAction(
    "Migrate Dependencies to Version Catalog",
    "Scan all .gradle.kts files and migrate hardcoded dependencies to libs.versions.toml",
    null
) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Migrating Dependencies to Version Catalog",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                indicator.text = "Scanning .gradle.kts files..."

                val migrator = VersionCatalogMigrator(project)
                val result = migrator.migrate(indicator)

                ApplicationManager.getApplication().invokeLater {
                    showResult(project, result)
                }
            }
        })
    }

    private fun showResult(project: Project, result: MigrationResult) {
        when {
            result.error != null -> {
                Messages.showErrorDialog(
                    project,
                    result.error,
                    "Migration Failed"
                )
            }

            result.totalDependencies == 0 -> {
                Messages.showInfoMessage(
                    project,
                    "No hardcoded dependencies found in .gradle.kts files.",
                    "Migration Complete"
                )
            }

            else -> {
                val message = buildString {
                    appendLine("Migration completed successfully!")
                    appendLine()
                    appendLine("Summary:")
                    appendLine("  - Scanned files: ${result.scannedFiles}")
                    appendLine("  - Dependencies found: ${result.totalDependencies}")
                    appendLine("  - Unique artifacts: ${result.uniqueArtifacts}")
                    appendLine("  - Files modified: ${result.modifiedFiles}")
                    appendLine()
                    val catalogPath = GradleBuddySettingsService.getInstance(project).getVersionCatalogPath()
                    appendLine("Generated: $catalogPath")
                    if (result.warnings.isNotEmpty()) {
                        appendLine()
                        appendLine("Warnings:")
                        result.warnings.take(5).forEach { appendLine("  - $it") }
                        if (result.warnings.size > 5) {
                            appendLine("  - ... and ${result.warnings.size - 5} more")
                        }
                    }
                }

                val openFile = Messages.showYesNoDialog(
                    project,
                    message,
                    "Migration Complete",
                    "Open libs.versions.toml",
                    "Close",
                    Messages.getInformationIcon()
                )

                if (openFile == Messages.YES) {
                    result.catalogFile?.let { file ->
                        FileEditorManager.getInstance(project).openFile(file, true)
                    }
                }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

/**
 * Version Catalog 迁移器
 */
class VersionCatalogMigrator(private val project: Project) {

    companion object {
        private val DEPENDENCY_PATTERN = Regex(
            """(implementation|api|compileOnly|runtimeOnly|testImplementation|testCompileOnly|testRuntimeOnly|kapt|ksp|annotationProcessor|classpath)\s*\(\s*"([^"]+)"\s*\)"""
        )

        private val COORDINATE_PATTERN = Regex("""^([^:]+):([^:]+):([^:]+)$""")
    }

    fun migrate(indicator: ProgressIndicator): MigrationResult {
        try {
            indicator.text = "Scanning .gradle.kts files..."
            indicator.fraction = 0.1

            val ktsFiles = findAllKtsFiles()
            if (ktsFiles.isEmpty()) {
                return MigrationResult(error = "No .gradle.kts files found in project")
            }

            indicator.text = "Extracting dependencies..."
            indicator.fraction = 0.3

            val allDependencies = mutableListOf<ExtractedDependency>()
            ktsFiles.forEach { file ->
                val deps = extractDependencies(file)
                allDependencies.addAll(deps)
            }

            if (allDependencies.isEmpty()) {
                return MigrationResult(
                    scannedFiles = ktsFiles.size,
                    totalDependencies = 0
                )
            }

            indicator.text = "Generating version catalog..."
            indicator.fraction = 0.5

            val catalog = generateCatalog(allDependencies)

            indicator.text = "Writing libs.versions.toml..."
            indicator.fraction = 0.7

            val catalogFile = writeCatalogFile(catalog)

            indicator.text = "Updating .gradle.kts files..."
            indicator.fraction = 0.9

            val modifiedFiles = replaceInKtsFiles(ktsFiles, catalog)

            indicator.fraction = 1.0

            return MigrationResult(
                scannedFiles = ktsFiles.size,
                totalDependencies = allDependencies.size,
                uniqueArtifacts = catalog.libraries.size,
                modifiedFiles = modifiedFiles,
                catalogFile = catalogFile,
                warnings = catalog.warnings
            )
        } catch (e: Exception) {
            return MigrationResult(error = "Migration failed: ${e.message}")
        }
    }

    private fun findAllKtsFiles(): List<VirtualFile> {
        val basePath = project.basePath ?: return emptyList()
        val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return emptyList()

        val ktsFiles = mutableListOf<VirtualFile>()
        VfsUtil.iterateChildrenRecursively(baseDir, { file ->
            !file.name.startsWith(".") && file.name != "build" && file.name != "node_modules"
        }) { file ->
            if (file.name.endsWith(".gradle.kts") && !file.path.contains("/build/")) {
                ktsFiles.add(file)
            }
            true
        }
        return ktsFiles
    }

    private fun extractDependencies(file: VirtualFile): List<ExtractedDependency> {
        val content = String(file.contentsToByteArray())
        val dependencies = mutableListOf<ExtractedDependency>()

        DEPENDENCY_PATTERN.findAll(content).forEach { match ->
            val method = match.groupValues[1]
            val coordinate = match.groupValues[2]

            COORDINATE_PATTERN.matchEntire(coordinate)?.let { coordMatch ->
                dependencies.add(
                    ExtractedDependency(
                        file = file,
                        method = method,
                        groupId = coordMatch.groupValues[1],
                        artifactId = coordMatch.groupValues[2],
                        version = coordMatch.groupValues[3],
                        fullMatch = match.value,
                        range = match.range
                    )
                )
            }
        }

        return dependencies
    }

    private fun generateCatalog(dependencies: List<ExtractedDependency>): CatalogData {
        val warnings = mutableListOf<String>()
        val versions = mutableMapOf<String, String>()
        val libraries = mutableMapOf<String, LibraryEntry>()

        val grouped = dependencies.groupBy { "${it.groupId}:${it.artifactId}" }

        grouped.forEach { (coordinate, deps) ->
            val first = deps.first()
            val allVersions = deps.map { it.version }.distinct()

            if (allVersions.size > 1) {
                warnings.add("$coordinate has multiple versions: ${allVersions.joinToString()}, using ${first.version}")
            }

            val alias = generateAlias(first.groupId, first.artifactId)
            val versionRef = generateVersionRef(first.groupId, first.artifactId)

            versions[versionRef] = first.version
            libraries[alias] = LibraryEntry(
                alias = alias,
                groupId = first.groupId,
                artifactId = first.artifactId,
                versionRef = versionRef
            )
        }

        return CatalogData(
            versions = versions,
            libraries = libraries,
            warnings = warnings,
            dependencyMap = dependencies.associateBy { "${it.groupId}:${it.artifactId}" }
        )
    }

    private fun generateAlias(groupId: String, artifactId: String): String {
        return artifactId
            .replace(".", "-")
            .replace("_", "-")
            .lowercase()
    }

    private fun generateVersionRef(groupId: String, artifactId: String): String {
        // 直接使用 artifactId 的 kebab-case 形式作为 version.ref 的基础
        return "${artifactId.replace(".", "-").replace("_", "-").lowercase()}-version"
    }

    private fun writeCatalogFile(catalog: CatalogData): VirtualFile? {
        // 解析路径：分离目录和文件名
        val catalogFile = GradleBuddySettingsService.getInstance(project).resolveVersionCatalogFile(project)
        val catalogDir = catalogFile.parentFile

        // 确保目录存在
        if (!catalogDir.exists()) {
            catalogDir.mkdirs()
        }

        val existingContent = if (catalogFile.exists()) {
            parseExistingCatalog(catalogFile.readText())
        } else {
            ExistingCatalog()
        }

        val content = buildString {
            appendLine("[versions]")
            val allVersions = existingContent.versions + catalog.versions
            allVersions.toSortedMap().forEach { (name, version) ->
                appendLine("$name = \"$version\"")
            }

            appendLine()
            appendLine("[libraries]")
            val allLibraries = existingContent.libraries + catalog.libraries
            allLibraries.toSortedMap().forEach { (alias, entry) ->
                appendLine("$alias = { group = \"${entry.groupId}\", name = \"${entry.artifactId}\", version.ref = \"${entry.versionRef}\" }")
            }
        }

        catalogFile.writeText(content)

        LocalFileSystem.getInstance().refreshAndFindFileByPath(catalogFile.absolutePath)?.let {
            return it
        }

        return null
    }

    private fun parseExistingCatalog(content: String): ExistingCatalog {
        val versions = mutableMapOf<String, String>()
        val libraries = mutableMapOf<String, LibraryEntry>()

        var inVersions = false
        var inLibraries = false

        content.lines().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed == "[versions]" -> {
                    inVersions = true; inLibraries = false
                }

                trimmed == "[libraries]" -> {
                    inVersions = false; inLibraries = true
                }

                trimmed.startsWith("[") -> {
                    inVersions = false; inLibraries = false
                }

                inVersions && trimmed.contains("=") -> {
                    val parts = trimmed.split("=", limit = 2)
                    if (parts.size == 2) {
                        val name = parts[0].trim()
                        val version = parts[1].trim().removeSurrounding("\"")
                        versions[name] = version
                    }
                }

                inLibraries && trimmed.contains("=") -> {
                    val aliasMatch = Regex("""^([\w-]+)\s*=""").find(trimmed)
                    val groupMatch = Regex("""group\s*=\s*"([^"]+)"""").find(trimmed)
                    val nameMatch = Regex("""name\s*=\s*"([^"]+)"""").find(trimmed)
                    val versionRefMatch = Regex("""version\.ref\s*=\s*"([^"]+)"""").find(trimmed)

                    if (aliasMatch != null && groupMatch != null && nameMatch != null) {
                        val alias = aliasMatch.groupValues[1]
                        libraries[alias] = LibraryEntry(
                            alias = alias,
                            groupId = groupMatch.groupValues[1],
                            artifactId = nameMatch.groupValues[1],
                            versionRef = versionRefMatch?.groupValues?.get(1) ?: alias
                        )
                    }
                }
            }
        }

        return ExistingCatalog(versions, libraries)
    }

    private fun replaceInKtsFiles(files: List<VirtualFile>, catalog: CatalogData): Int {
        var modifiedCount = 0

        WriteCommandAction.runWriteCommandAction(project) {
            files.forEach { file ->
                var content = String(file.contentsToByteArray())
                var modified = false

                catalog.libraries.forEach { (alias, entry) ->
                    val coordinate = "${entry.groupId}:${entry.artifactId}"
                    val libsAccessor = alias.replace("-", ".")

                    val methods = listOf(
                        "implementation", "api", "compileOnly", "runtimeOnly",
                        "testImplementation", "testCompileOnly", "testRuntimeOnly",
                        "kapt", "ksp", "annotationProcessor", "classpath"
                    )

                    methods.forEach { method ->
                        val pattern = Regex("""$method\s*\(\s*"${Regex.escape(coordinate)}:[^"]+"\s*\)""")
                        if (pattern.containsMatchIn(content)) {
                            content = pattern.replace(content, "$method(libs.$libsAccessor)")
                            modified = true
                        }
                    }
                }

                if (modified) {
                    file.setBinaryContent(content.toByteArray())
                    modifiedCount++
                }
            }
        }

        return modifiedCount
    }
}

data class ExtractedDependency(
    val file: VirtualFile,
    val method: String,
    val groupId: String,
    val artifactId: String,
    val version: String,
    val fullMatch: String,
    val range: IntRange
)

data class LibraryEntry(
    val alias: String,
    val groupId: String,
    val artifactId: String,
    val versionRef: String
)

data class CatalogData(
    val versions: Map<String, String>,
    val libraries: Map<String, LibraryEntry>,
    val warnings: List<String>,
    val dependencyMap: Map<String, ExtractedDependency>
)

data class ExistingCatalog(
    val versions: Map<String, String> = emptyMap(),
    val libraries: Map<String, LibraryEntry> = emptyMap()
)

data class MigrationResult(
    val scannedFiles: Int = 0,
    val totalDependencies: Int = 0,
    val uniqueArtifacts: Int = 0,
    val modifiedFiles: Int = 0,
    val catalogFile: VirtualFile? = null,
    val warnings: List<String> = emptyList(),
    val error: String? = null
)

package site.addzero.gradle.buddy.intentions.convert

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle
import site.addzero.gradle.buddy.settings.GradleBuddySettingsService
import java.io.File

/**
 * 复用“硬编码依赖 -> 版本目录”相关的解析、规划与 TOML upsert 逻辑。
 */
internal object HardcodedDependencyCatalogSupport {

    enum class VersionReferencePolicy {
        REUSE_MATCHING,
        DEDICATED
    }

    fun isTargetGradleKtsFile(file: PsiFile): Boolean {
        return file.name.endsWith(".gradle.kts")
    }

    fun findHardcodedDependency(element: PsiElement): DependencyInfo? {
        val callExpr = element.parentOfType<KtCallExpression>(true) ?: return null
        return findHardcodedDependency(callExpr)
    }

    fun findHardcodedDependency(callExpr: KtCallExpression): DependencyInfo? {
        val callee = callExpr.calleeExpression?.text ?: return null
        return when {
            isDependencyConfiguration(callee) -> extractDependencyInfo(
                callExpr = callExpr,
                configuration = callee,
                argumentExpression = callExpr.valueArguments.firstOrNull()?.getArgumentExpression() as? KtStringTemplateExpression
            )

            callee == "add" -> extractAddDependencyInfo(callExpr)

            else -> null
        }
    }

    fun chooseVersionReference(
        project: Project,
        editor: Editor?,
        info: DependencyInfo,
        versionPolicy: VersionReferencePolicy = VersionReferencePolicy.REUSE_MATCHING,
        onResolved: (catalogFile: File, existingContent: VersionCatalogContent, versionRefFromVar: String?, selectedVersionKey: String?) -> Unit
    ) {
        val versionRefFromVar = extractVersionRef(info.version)
        val (catalogFile, existingContent) = loadVersionCatalog(project)

        if (versionRefFromVar != null) {
            onResolved(catalogFile, existingContent, versionRefFromVar, null)
            return
        }

        if (versionPolicy == VersionReferencePolicy.DEDICATED || editor == null) {
            onResolved(catalogFile, existingContent, null, null)
            return
        }

        val matchingVersionEntries = existingContent.versions.filterValues { it == info.version }
        if (matchingVersionEntries.isEmpty()) {
            onResolved(catalogFile, existingContent, null, null)
            return
        }

        val newVersionKey = generateVersionKey(info.groupId, info.artifactId)
        val createNewLabel = GradleBuddyBundle.message(
            "intention.hardcoded.dependency.to.toml.version.create.new",
            newVersionKey,
            info.version
        )
        val items = matchingVersionEntries.map { (key, ver) -> "$key = \"$ver\"" } + createNewLabel

        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(items)
            .setTitle(GradleBuddyBundle.message("intention.hardcoded.dependency.to.toml.version.popup.title"))
            .setAdText(
                GradleBuddyBundle.message(
                    "intention.hardcoded.dependency.to.toml.version.popup.ad",
                    matchingVersionEntries.size,
                    info.version
                )
            )
            .setItemChosenCallback { chosen ->
                val selectedVersionKey = if (chosen == createNewLabel) {
                    null
                } else {
                    chosen.substringBefore(" =").trim()
                }
                onResolved(catalogFile, existingContent, null, selectedVersionKey)
            }
            .createPopup()
            .showInBestPositionFor(editor)
    }

    fun prepareCatalogEntry(
        existingContent: VersionCatalogContent,
        info: DependencyInfo,
        versionRefFromVar: String?,
        selectedVersionKey: String?,
        versionPolicy: VersionReferencePolicy = VersionReferencePolicy.REUSE_MATCHING
    ): PreparedCatalogEntry {
        val existingLibrary = existingContent.libraries.values.firstOrNull { entry ->
            entry.groupId == info.groupId && entry.artifactId == info.artifactId
        }
        val libraryKey = if (existingLibrary != null) {
            existingLibrary.alias
        } else {
            val candidateKey = generateLibraryKey(info.groupId, info.artifactId)
            val conflicting = existingContent.libraries[candidateKey]
            if (conflicting != null && (conflicting.groupId != info.groupId || conflicting.artifactId != info.artifactId)) {
                generateDisambiguatedLibraryKey(info.groupId, info.artifactId)
            } else {
                candidateKey
            }
        }

        val dedicatedVersionKey = generateVersionKey(libraryKey)
        val versionKey = when {
            versionRefFromVar != null -> versionRefFromVar
            selectedVersionKey != null -> selectedVersionKey
            versionPolicy == VersionReferencePolicy.DEDICATED -> dedicatedVersionKey
            !existingLibrary?.versionRef.isNullOrBlank() -> existingLibrary.versionRef.orEmpty()
            else -> dedicatedVersionKey
        }

        val needCreateVersion = versionRefFromVar == null && !existingContent.versions.containsKey(versionKey)
        val syncVersionValue = versionRefFromVar == null &&
            selectedVersionKey == null &&
            versionPolicy == VersionReferencePolicy.DEDICATED

        return PreparedCatalogEntry(
            libraryKey = libraryKey,
            accessorKey = toCatalogAccessor(libraryKey),
            versionKey = versionKey,
            needCreateVersion = needCreateVersion,
            syncVersionValue = syncVersionValue
        )
    }

    fun selectVersionReferenceForBatch(
        existingContent: VersionCatalogContent,
        info: DependencyInfo,
        versionPolicy: VersionReferencePolicy = VersionReferencePolicy.REUSE_MATCHING
    ): BatchVersionSelection {
        val versionRefFromVar = extractVersionRef(info.version)
        if (versionRefFromVar != null) {
            return BatchVersionSelection(
                versionRefFromVar = versionRefFromVar,
                selectedVersionKey = null
            )
        }

        if (versionPolicy == VersionReferencePolicy.DEDICATED) {
            return BatchVersionSelection(
                versionRefFromVar = null,
                selectedVersionKey = null
            )
        }

        val matches = existingContent.versions
            .filterValues { it == info.version }
            .keys
            .sorted()
        if (matches.isEmpty()) {
            return BatchVersionSelection(
                versionRefFromVar = null,
                selectedVersionKey = null
            )
        }

        val preferredKey = generateVersionKey(info.groupId, info.artifactId)
        val selectedKey = when {
            preferredKey in matches -> preferredKey
            else -> matches.first()
        }
        return BatchVersionSelection(
            versionRefFromVar = null,
            selectedVersionKey = selectedKey
        )
    }

    fun registerCatalogEntry(
        existingContent: VersionCatalogContent,
        info: DependencyInfo,
        prepared: PreparedCatalogEntry
    ): CatalogMutationResult {
        var createdVersion = false
        var createdLibrary = false

        if (prepared.needCreateVersion || (prepared.syncVersionValue && existingContent.versions[prepared.versionKey] != info.version)) {
            val existed = existingContent.versions.containsKey(prepared.versionKey)
            existingContent.versions[prepared.versionKey] = info.version
            createdVersion = !existed
        }

        val desiredLibrary = LibraryEntry(
            alias = prepared.libraryKey,
            groupId = info.groupId,
            artifactId = info.artifactId,
            module = "${info.groupId}:${info.artifactId}",
            versionRef = prepared.versionKey,
            version = null,
            classifier = info.classifier
        )

        if (!existingContent.libraries.containsKey(prepared.libraryKey)) {
            existingContent.libraries[prepared.libraryKey] = desiredLibrary
            createdLibrary = true
        } else if (existingContent.libraries[prepared.libraryKey] != desiredLibrary) {
            existingContent.libraries[prepared.libraryKey] = desiredLibrary
        }

        return CatalogMutationResult(
            createdVersion = createdVersion,
            createdLibrary = createdLibrary
        )
    }

    fun loadVersionCatalog(project: Project): Pair<File, VersionCatalogContent> {
        val catalogFile = GradleBuddySettingsService.getInstance(project).resolveVersionCatalogFile(project)
        val existingContent = if (catalogFile.exists()) {
            parseVersionCatalog(catalogFile.readText())
        } else {
            VersionCatalogContent(
                versions = mutableMapOf(),
                libraries = mutableMapOf()
            )
        }
        return catalogFile to existingContent
    }

    fun mergeToVersionCatalog(
        catalogFile: File,
        existingContent: VersionCatalogContent,
        info: DependencyInfo,
        prepared: PreparedCatalogEntry
    ) {
        val catalogDir = catalogFile.parentFile
        if (catalogDir != null && !catalogDir.exists()) {
            catalogDir.mkdirs()
        }

        registerCatalogEntry(existingContent, info, prepared)

        if (catalogFile.exists()) {
            updateVersionCatalogInPlace(catalogFile, info, prepared)
        } else {
            writeVersionCatalog(catalogFile, existingContent)
        }

        LocalFileSystem.getInstance().refreshAndFindFileByPath(catalogFile.absolutePath)
    }

    fun extractVersionRef(version: String): String? {
        val trimmed = version.trim()
        val regex = Regex("""\$\{?\s*libs\.versions\.([A-Za-z0-9_.-]+)\.get\(\)\s*}?\s*""")
        val match = regex.matchEntire(trimmed)
        if (match != null) {
            return match.groupValues[1]
        }
        val alt = Regex("""libs\.versions\.([A-Za-z0-9_.-]+)""")
        return alt.matchEntire(trimmed)?.groupValues?.get(1)
    }

    fun isSupportedForFindLibrary(info: DependencyInfo): Boolean {
        if (info.classifier != null || info.extension != null) {
            return false
        }
        if (info.groupId.contains('$') || info.artifactId.contains('$')) {
            return false
        }
        if (info.version.contains('$') && extractVersionRef(info.version) == null) {
            return false
        }
        return true
    }

    fun containsHardcodedDependencyText(text: String): Boolean {
        return HARD_CODED_DEPENDENCY_CALL_REGEX.containsMatchIn(text)
    }

    private fun isDependencyConfiguration(callee: String): Boolean {
        return callee in DEPENDENCY_CONFIGURATIONS
    }

    private fun extractAddDependencyInfo(callExpr: KtCallExpression): DependencyInfo? {
        val configExpression = callExpr.valueArguments.getOrNull(0)?.getArgumentExpression() as? KtStringTemplateExpression
            ?: return null
        val configuration = extractLiteralString(configExpression)?.trim().orEmpty()
        if (configuration.isBlank()) {
            return null
        }

        val dependencyExpression = callExpr.valueArguments.getOrNull(1)?.getArgumentExpression() as? KtStringTemplateExpression
            ?: return null
        return extractDependencyInfo(
            callExpr = callExpr,
            configuration = configuration,
            argumentExpression = dependencyExpression
        )
    }

    private fun extractDependencyInfo(
        callExpr: KtCallExpression,
        configuration: String,
        argumentExpression: KtStringTemplateExpression?
    ): DependencyInfo? {
        val argExpression = argumentExpression ?: return null
        val dependencyString = argExpression.text.trim('"', '\'')

        val parts = dependencyString.split(":")
        if (parts.size < 3) {
            return null
        }

        val groupId = parts[0]
        val artifactId = parts[1]
        val version = parts[2]
        var classifier: String? = null
        var extension: String? = null

        if (parts.size > 3) {
            if (parts[3].contains("@")) {
                val extParts = parts[3].split("@")
                extension = extParts[0]
                classifier = extParts.getOrNull(1)
            } else {
                extension = parts[3]
                if (parts.size > 4 && parts[4].startsWith("@")) {
                    classifier = parts[4].substring(1)
                }
            }
        }

        if (dependencyString.startsWith("libs.") || dependencyString.contains(".libs.")) {
            return null
        }

        return DependencyInfo(
            groupId = groupId,
            artifactId = artifactId,
            version = version,
            classifier = classifier,
            extension = extension,
            configuration = configuration,
            callExpression = callExpr,
            argumentExpression = argExpression
        )
    }

    private fun extractLiteralString(expression: KtStringTemplateExpression): String? {
        if (expression.entries.any { entry -> entry !is org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry }) {
            return null
        }
        return expression.entries.joinToString(separator = "") { it.text }
    }

    private fun parseVersionCatalog(content: String): VersionCatalogContent {
        val versions = mutableMapOf<String, String>()
        val libraries = mutableMapOf<String, LibraryEntry>()

        var inVersions = false
        var inLibraries = false

        content.lines().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed == "[versions]" -> {
                    inVersions = true
                    inLibraries = false
                }

                trimmed == "[libraries]" -> {
                    inVersions = false
                    inLibraries = true
                }

                trimmed.startsWith("[") -> {
                    inVersions = false
                    inLibraries = false
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
                    val moduleMatch = Regex("""module\s*=\s*"([^"]+)"""").find(trimmed)
                    val groupMatch = Regex("""group\s*=\s*"([^"]+)"""").find(trimmed)
                    val nameMatch = Regex("""name\s*=\s*"([^"]+)"""").find(trimmed)
                    val versionRefMatch = Regex("""version\.ref\s*=\s*"([^"]+)"""").find(trimmed)
                    val versionMatch = Regex("""version\s*=\s*"([^"]+)"""").find(trimmed)
                    val classifierMatch = Regex("""classifier\s*=\s*"([^"]+)"""").find(trimmed)

                    if (aliasMatch != null && (moduleMatch != null || (groupMatch != null && nameMatch != null))) {
                        val alias = aliasMatch.groupValues[1]
                        val module = moduleMatch?.groupValues?.get(1)
                        val groupId = groupMatch?.groupValues?.get(1) ?: module?.substringBefore(":")
                        val artifactId = nameMatch?.groupValues?.get(1) ?: module?.substringAfter(":")
                        if (groupId == null || artifactId == null) {
                            return@forEach
                        }
                        libraries[alias] = LibraryEntry(
                            alias = alias,
                            groupId = groupId,
                            artifactId = artifactId,
                            module = module,
                            versionRef = versionRefMatch?.groupValues?.get(1),
                            version = versionMatch?.groupValues?.get(1),
                            classifier = classifierMatch?.groupValues?.get(1)
                        )
                    }
                }
            }
        }

        return VersionCatalogContent(versions, libraries)
    }

    private fun writeVersionCatalog(file: File, content: VersionCatalogContent) {
        val tomlBuilder = StringBuilder()

        tomlBuilder.appendLine("[versions]")
        content.versions.toSortedMap().forEach { (name, version) ->
            tomlBuilder.appendLine("$name = \"$version\"")
        }

        tomlBuilder.appendLine()
        tomlBuilder.appendLine("[libraries]")
        content.libraries.toSortedMap().forEach { (alias, entry) ->
            tomlBuilder.append("$alias = { group = \"${entry.groupId}\", name = \"${entry.artifactId}\"")
            if (!entry.versionRef.isNullOrBlank()) {
                tomlBuilder.append(", version.ref = \"${entry.versionRef}\"")
            } else if (!entry.version.isNullOrBlank()) {
                tomlBuilder.append(", version = \"${entry.version}\"")
            }
            if (entry.classifier != null) {
                tomlBuilder.append(", classifier = \"${entry.classifier}\"")
            }
            tomlBuilder.appendLine(" }")
        }

        file.writeText(tomlBuilder.toString())
    }

    private fun updateVersionCatalogInPlace(
        file: File,
        info: DependencyInfo,
        prepared: PreparedCatalogEntry
    ) {
        val original = file.readText()
        var updatedLines = original.lines().toMutableList()

        if (prepared.needCreateVersion || prepared.syncVersionValue) {
            updatedLines = upsertVersionEntry(
                lines = updatedLines,
                versionKey = prepared.versionKey,
                version = info.version,
                replaceExisting = prepared.syncVersionValue
            )
        }
        updatedLines = upsertLibraryEntry(updatedLines, prepared.libraryKey, info, prepared.versionKey)

        val updated = updatedLines.joinToString("\n")
        if (updated != original) {
            file.writeText(updated)
        }
    }

    private fun upsertVersionEntry(
        lines: MutableList<String>,
        versionKey: String,
        version: String,
        replaceExisting: Boolean
    ): MutableList<String> {
        val section = findSection(lines, "[versions]")
        val entryRegex = Regex("""^\s*${Regex.escape(versionKey)}\s*=""")
        if (section != null) {
            val existingIndex = (section.start + 1 until section.end).firstOrNull { index ->
                entryRegex.containsMatchIn(lines[index])
            }
            if (existingIndex != null) {
                if (replaceExisting) {
                    lines[existingIndex] = "$versionKey = \"$version\""
                }
                return lines
            }
            lines.add(section.end, "$versionKey = \"$version\"")
            return lines
        }

        if (lines.isNotEmpty() && lines.last().isNotBlank()) {
            lines.add("")
        }
        lines.add("[versions]")
        lines.add("$versionKey = \"$version\"")
        return lines
    }

    private fun upsertLibraryEntry(
        lines: MutableList<String>,
        libraryKey: String,
        info: DependencyInfo,
        versionKey: String
    ): MutableList<String> {
        val section = findSection(lines, "[libraries]")
        val entryRegex = Regex("""^\s*${Regex.escape(libraryKey)}\s*=""")
        val newLine = buildLibraryLine(libraryKey, info, versionKey)
        if (section != null) {
            val existingIndex = (section.start + 1 until section.end).firstOrNull { index ->
                entryRegex.containsMatchIn(lines[index])
            }
            if (existingIndex != null) {
                if (lines[existingIndex] != newLine) {
                    lines[existingIndex] = newLine
                }
                return lines
            }
            lines.add(section.end, newLine)
            return lines
        }

        if (lines.isNotEmpty() && lines.last().isNotBlank()) {
            lines.add("")
        }
        lines.add("[libraries]")
        lines.add(newLine)
        return lines
    }

    private fun buildLibraryLine(
        libraryKey: String,
        info: DependencyInfo,
        versionKey: String
    ): String {
        val builder = StringBuilder()
        builder.append("$libraryKey = { group = \"${info.groupId}\", name = \"${info.artifactId}\"")
        builder.append(", version.ref = \"$versionKey\"")
        if (info.classifier != null) {
            builder.append(", classifier = \"${info.classifier}\"")
        }
        builder.append(" }")
        return builder.toString()
    }

    private fun findSection(lines: List<String>, header: String): SectionRange? {
        var start = -1
        for (i in lines.indices) {
            if (lines[i].trim() == header) {
                start = i
                break
            }
        }
        if (start == -1) {
            return null
        }
        var end = lines.size
        for (i in start + 1 until lines.size) {
            val trimmed = lines[i].trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                end = i
                break
            }
        }
        return SectionRange(start, end)
    }

    private fun generateLibraryKey(groupId: String, artifactId: String): String {
        return artifactId
            .replace(".", "-")
            .replace("_", "-")
            .lowercase()
    }

    private fun generateDisambiguatedLibraryKey(groupId: String, artifactId: String): String {
        val groupSuffix = groupId.substringAfterLast(".")
        val base = artifactId.replace(".", "-").replace("_", "-").lowercase()
        return "${groupSuffix}-${base}".lowercase()
    }

    private fun toCatalogAccessor(alias: String): String {
        return alias.replace('-', '.').replace('_', '.')
    }

    fun generateVersionKey(groupId: String, artifactId: String): String = generateVersionKey(generateLibraryKey(groupId, artifactId))

    fun generateVersionKey(libraryKey: String): String = libraryKey

    data class DependencyInfo(
        val groupId: String,
        val artifactId: String,
        val version: String,
        val classifier: String?,
        val extension: String?,
        val configuration: String,
        val callExpression: KtCallExpression,
        val argumentExpression: KtStringTemplateExpression
    )

    data class PreparedCatalogEntry(
        val libraryKey: String,
        val accessorKey: String,
        val versionKey: String,
        val needCreateVersion: Boolean,
        val syncVersionValue: Boolean
    )

    data class BatchVersionSelection(
        val versionRefFromVar: String?,
        val selectedVersionKey: String?
    )

    data class CatalogMutationResult(
        val createdVersion: Boolean,
        val createdLibrary: Boolean
    )

    data class VersionCatalogContent(
        val versions: MutableMap<String, String>,
        val libraries: MutableMap<String, LibraryEntry>
    )

    data class LibraryEntry(
        val alias: String,
        val groupId: String,
        val artifactId: String,
        val module: String? = null,
        val versionRef: String? = null,
        val version: String? = null,
        val classifier: String? = null
    )

    private data class SectionRange(
        val start: Int,
        val end: Int
    )

    private val DEPENDENCY_CONFIGURATIONS = setOf(
        "implementation", "api", "compileOnly", "runtimeOnly",
        "testImplementation", "testApi", "testCompileOnly", "testRuntimeOnly",
        "androidTestImplementation", "androidTestApi", "androidTestCompileOnly", "androidTestRuntimeOnly",
        "debugImplementation", "releaseImplementation", "kapt", "ksp",
        "annotationProcessor", "lintChecks"
    )

    private val HARD_CODED_DEPENDENCY_CALL_REGEX = Regex(
        """(?x)
        \b(
            (${DEPENDENCY_CONFIGURATIONS.joinToString("|")})\s*\(\s*["'][^"'\n]+:[^"'\n]+:[^"'\n]+["']\s*\)
            |
            add\s*\(\s*["'][^"'\n]+["']\s*,\s*["'][^"'\n]+:[^"'\n]+:[^"'\n]+["']\s*\)
        )
        """
    )
}

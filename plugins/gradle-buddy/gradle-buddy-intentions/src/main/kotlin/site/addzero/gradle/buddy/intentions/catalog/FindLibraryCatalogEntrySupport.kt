package site.addzero.gradle.buddy.intentions.catalog

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import site.addzero.gradle.buddy.intentions.convert.CatalogAccessorToFindLibrarySupport
import site.addzero.gradle.buddy.intentions.convert.HardcodedDependencyCatalogSupport
import site.addzero.gradle.buddy.settings.GradleBuddySettingsService
import java.io.File

/**
 * 统一承载 `libs.findLibrary("alias").get()` 的识别与 TOML 补全逻辑。
 *
 * 这里额外增强了一层工件坐标推断：
 * 1. 先复用当前 TOML 中已有条目；
 * 2. 再扫描项目内硬编码依赖，优先反推同 alias 的真实 group/artifact/version；
 * 3. 最后才退回到 alias 规则猜测。
 */
internal object FindLibraryCatalogEntrySupport {

    fun findTarget(file: PsiFile, offset: Int): TargetCall? {
        val offsets = buildList {
            if (offset in 0 until file.textLength) {
                add(offset)
            }
            if (offset > 0 && offset - 1 in 0 until file.textLength) {
                add(offset - 1)
            }
        }.distinct()

        for (candidateOffset in offsets) {
            val element = file.findElementAt(candidateOffset) ?: continue
            findTarget(element)?.let { return it }
        }
        return null
    }

    fun findTarget(element: PsiElement): TargetCall? {
        val stringExpression = PsiTreeUtil.getParentOfType(element, KtStringTemplateExpression::class.java, false)
            ?: return null
        return resolveTarget(stringExpression)
    }

    fun extractAliasFromLine(lineText: String): String? {
        return FIND_LIBRARY_ALIAS_REGEX.find(lineText)?.groupValues?.get(1)?.takeIf(String::isNotBlank)
    }

    fun ensureCatalogEntry(
        project: Project,
        alias: String,
        preferredVersion: String? = null,
        preferredBuildFile: VirtualFile? = null
    ): NavigationTarget {
        val catalogFile = GradleBuddySettingsService.getInstance(project).resolveVersionCatalogFile(project)
        return ensureCatalogEntry(
            project = project,
            catalogFile = catalogFile,
            alias = alias,
            preferredVersion = preferredVersion,
            preferredBuildFile = preferredBuildFile
        )
    }

    fun ensureCatalogEntry(
        project: Project,
        catalogFile: File,
        alias: String,
        preferredVersion: String? = null,
        preferredBuildFile: VirtualFile? = null
    ): NavigationTarget {
        catalogFile.parentFile?.mkdirs()
        if (!catalogFile.exists()) {
            catalogFile.writeText("")
        }

        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(catalogFile)
            ?: error("Cannot refresh version catalog file: ${catalogFile.absolutePath}")
        val document = FileDocumentManager.getInstance().getDocument(virtualFile)
        val currentText = document?.text ?: catalogFile.readText()
        val existingEntries = parseLibraryEntries(currentText)
        val existingVersionEntries = parseVersionEntries(currentText)
        val dedicatedVersionKey = HardcodedDependencyCatalogSupport.generateVersionKey(alias)

        val existing = existingEntries.firstOrNull { it.alias == alias }
        val resolved = existing?.let {
            ResolvedCatalogEntry(
                groupId = it.groupId,
                artifactId = it.artifactId,
                versionValue = resolveExistingVersionValue(it, existingVersionEntries)
            )
        } ?: resolveFromProject(project, alias, existingEntries, existingVersionEntries, preferredBuildFile)

        val coordinates = resolved ?: guessCoordinates(alias, existingEntries)
        val versionValue = preferredVersion
            ?: resolved?.versionValue
            ?: existing?.let { resolveExistingVersionValue(it, existingVersionEntries) }
            ?: "+"

        val updatedText = upsertVersionEntry(
            originalText = upsertLibraryEntry(
                originalText = currentText,
                alias = alias,
                groupId = coordinates.groupId,
                artifactId = coordinates.artifactId,
                versionKey = dedicatedVersionKey
            ),
            versionKey = dedicatedVersionKey,
            versionValue = versionValue
        )

        if (document != null) {
            document.replaceString(0, document.textLength, updatedText)
            PsiDocumentManager.getInstance(project).commitDocument(document)
            FileDocumentManager.getInstance().saveDocument(document)
        } else {
            catalogFile.writeText(updatedText)
        }

        val refreshedVirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(catalogFile) ?: virtualFile
        return NavigationTarget(
            virtualFile = refreshedVirtualFile,
            offset = findVersionValueOffset(updatedText, dedicatedVersionKey)
        )
    }

    private fun resolveTarget(stringExpression: KtStringTemplateExpression): TargetCall? {
        val alias = extractLiteralString(stringExpression) ?: return null
        if (alias.isBlank()) {
            return null
        }

        val callExpression = PsiTreeUtil.getParentOfType(stringExpression, KtCallExpression::class.java) ?: return null
        if (callExpression.valueArguments.singleOrNull()?.getArgumentExpression() != stringExpression) {
            return null
        }
        if (callExpression.calleeExpression?.text != "findLibrary") {
            return null
        }

        val receiver = (callExpression.parent as? KtDotQualifiedExpression)?.receiverExpression?.text?.trim()
        if (receiver.isNullOrBlank()) {
            return null
        }

        return TargetCall(alias)
    }

    private fun extractLiteralString(expression: KtStringTemplateExpression): String? {
        if (expression.entries.any { it !is KtLiteralStringTemplateEntry }) {
            return null
        }
        return expression.entries.joinToString(separator = "") { it.text }
    }

    private fun resolveFromProject(
        project: Project,
        alias: String,
        existingEntries: List<CatalogLibraryEntry>,
        existingVersionEntries: Map<String, CatalogVersionEntry>,
        preferredBuildFile: VirtualFile?
    ): ResolvedCatalogEntry? {
        val existingMatch = resolveFromExistingEntries(alias, existingEntries, existingVersionEntries)
        val projectMatch = resolveFromHardcodedDependencies(project, alias, existingVersionEntries, preferredBuildFile)

        return listOfNotNull(existingMatch, projectMatch)
            .maxByOrNull { it.score }
            ?.copy(score = 0)
    }

    private fun resolveFromExistingEntries(
        alias: String,
        existingEntries: List<CatalogLibraryEntry>,
        existingVersionEntries: Map<String, CatalogVersionEntry>
    ): ResolvedCatalogEntry? {
        return existingEntries
            .mapNotNull { entry ->
                val score = aliasMatchScore(alias, entry.groupId, entry.artifactId, entry.alias)
                if (score <= 0) {
                    return@mapNotNull null
                }
                ResolvedCatalogEntry(
                    groupId = entry.groupId,
                    artifactId = entry.artifactId,
                    versionValue = resolveExistingVersionValue(entry, existingVersionEntries),
                    score = score + 60
                )
            }
            .maxByOrNull { it.score }
    }

    private fun resolveFromHardcodedDependencies(
        project: Project,
        alias: String,
        existingVersionEntries: Map<String, CatalogVersionEntry>,
        preferredBuildFile: VirtualFile?
    ): ResolvedCatalogEntry? {
        val psiManager = PsiManager.getInstance(project)
        val files = buildList {
            if (preferredBuildFile != null) {
                add(preferredBuildFile)
            }
            addAll(
                CatalogAccessorToFindLibrarySupport.collectTargetGradleKtsFiles(project)
                    .filterNot { preferredBuildFile != null && it.path == preferredBuildFile.path }
            )
        }

        return files.asSequence()
            .mapNotNull { virtualFile ->
                val psiFile = psiManager.findFile(virtualFile) ?: return@mapNotNull null
                collectHardcodedDependencyCandidates(
                    psiFile = psiFile,
                    alias = alias,
                    existingVersionEntries = existingVersionEntries,
                    isPreferredFile = preferredBuildFile != null && virtualFile.path == preferredBuildFile.path
                )
            }
            .flatten()
            .maxByOrNull { it.score }
            ?.copy(score = 0)
    }

    private fun collectHardcodedDependencyCandidates(
        psiFile: PsiFile,
        alias: String,
        existingVersionEntries: Map<String, CatalogVersionEntry>,
        isPreferredFile: Boolean
    ): List<ResolvedCatalogEntry> {
        val candidates = mutableListOf<ResolvedCatalogEntry>()
        val callExpressions = PsiTreeUtil.collectElementsOfType(psiFile, KtCallExpression::class.java)
            .distinctBy { it.textRange.startOffset to it.textRange.endOffset }

        for (callExpression in callExpressions) {
            val dependency = HardcodedDependencyCatalogSupport.findHardcodedDependency(callExpression) ?: continue
            val score = aliasMatchScore(alias, dependency.groupId, dependency.artifactId, aliasFromDependency(dependency))
            if (score <= 0) {
                continue
            }
            candidates += ResolvedCatalogEntry(
                groupId = dependency.groupId,
                artifactId = dependency.artifactId,
                versionValue = resolveDependencyVersion(dependency.version, existingVersionEntries),
                score = score + if (isPreferredFile) 40 else 0
            )
        }

        return candidates
    }

    private fun resolveDependencyVersion(
        rawVersion: String,
        existingVersionEntries: Map<String, CatalogVersionEntry>
    ): String? {
        val versionRef = HardcodedDependencyCatalogSupport.extractVersionRef(rawVersion)
        if (versionRef != null) {
            return existingVersionEntries[versionRef]?.value
        }
        return rawVersion.takeIf { version -> version.isNotBlank() && '$' !in version }
    }

    private fun resolveExistingVersionValue(
        entry: CatalogLibraryEntry,
        existingVersionEntries: Map<String, CatalogVersionEntry>
    ): String? {
        return entry.directVersion
            ?: entry.versionRef?.let(existingVersionEntries::get)?.value
    }

    private fun aliasMatchScore(
        alias: String,
        groupId: String,
        artifactId: String,
        explicitAlias: String? = null
    ): Int {
        val normalizedAlias = alias.lowercase()
        val artifactAlias = toKebabCase(artifactId)
        val groupSuffixAlias = "${toKebabCase(groupId.substringAfterLast('.'))}-$artifactAlias"
        val fullGroupAlias = "${toKebabCase(groupId)}-$artifactAlias"
        val candidates = listOfNotNull(explicitAlias, artifactAlias, groupSuffixAlias, fullGroupAlias)
            .map { it.lowercase() }
            .distinct()

        return when {
            candidates.firstOrNull() == normalizedAlias -> 220
            normalizedAlias == artifactAlias -> 200
            normalizedAlias == groupSuffixAlias -> 180
            normalizedAlias == fullGroupAlias -> 160
            candidates.any { it == normalizedAlias } -> 140
            artifactAlias == normalizedAlias.removePrefix("${toKebabCase(groupId.substringAfterLast('.'))}-") -> 120
            else -> 0
        }
    }

    private fun aliasFromDependency(info: HardcodedDependencyCatalogSupport.DependencyInfo): String {
        return toKebabCase(info.artifactId)
    }

    private fun parseLibraryEntries(content: String): List<CatalogLibraryEntry> {
        val result = mutableListOf<CatalogLibraryEntry>()
        val lines = content.split('\n')
        var offset = 0
        var inLibraries = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                inLibraries = trimmed == "[libraries]"
                offset += line.length + 1
                continue
            }

            if (inLibraries && trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                parseLibraryEntryLine(line, offset)?.let(result::add)
            }
            offset += line.length + 1
        }

        return result
    }

    private fun parseLibraryEntryLine(line: String, lineStartOffset: Int): CatalogLibraryEntry? {
        val aliasMatch = Regex("""^\s*["']?([A-Za-z0-9_.-]+)["']?\s*=""").find(line) ?: return null
        val alias = aliasMatch.groupValues[1]
        val moduleMatch = Regex("module\\s*=\\s*\"([^\"]+):([^\"]+)\"").find(line)
        val groupMatch = Regex("group\\s*=\\s*\"([^\"]+)\"").find(line)
        val nameMatch = Regex("name\\s*=\\s*\"([^\"]+)\"").find(line)
        val versionRefMatch = Regex("version\\.ref\\s*=\\s*\"([^\"]+)\"").find(line)
        val versionMatch = Regex("(?<!\\.)version\\s*=\\s*\"([^\"]+)\"").find(line)
        val groupId = groupMatch?.groupValues?.get(1) ?: moduleMatch?.groupValues?.get(1) ?: return null
        val artifactId = nameMatch?.groupValues?.get(1) ?: moduleMatch?.groupValues?.get(2) ?: return null

        return CatalogLibraryEntry(
            alias = alias,
            groupId = groupId,
            artifactId = artifactId,
            versionRef = versionRefMatch?.groupValues?.get(1),
            directVersion = versionMatch?.groupValues?.get(1),
            lineText = line,
            lineStartOffset = lineStartOffset
        )
    }

    private fun parseVersionEntries(content: String): Map<String, CatalogVersionEntry> {
        val result = linkedMapOf<String, CatalogVersionEntry>()
        val lines = content.split('\n')
        var offset = 0
        var inVersions = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                inVersions = trimmed == "[versions]"
                offset += line.length + 1
                continue
            }

            if (inVersions && trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                val match = Regex("^\\s*[\"']?([A-Za-z0-9_.-]+)[\"']?\\s*=\\s*\"([^\"]*)\"").find(line)
                if (match != null) {
                    val key = match.groupValues[1]
                    val value = match.groupValues[2]
                    result[key] = CatalogVersionEntry(
                        key = key,
                        value = value,
                        lineText = line,
                        lineStartOffset = offset
                    )
                }
            }
            offset += line.length + 1
        }

        return result
    }

    private fun guessCoordinates(alias: String, existingEntries: List<CatalogLibraryEntry>): ResolvedCatalogEntry {
        inferFromExistingGroups(alias, existingEntries)?.let { return it }

        if (alias.startsWith("site-addzero-")) {
            return ResolvedCatalogEntry(
                groupId = "site.addzero",
                artifactId = alias.removePrefix("site-addzero-")
            )
        }

        val tokens = alias.split('-').filter { it.isNotBlank() }
        if (tokens.size >= 3 && tokens.first() in DOMAIN_PREFIXES) {
            return ResolvedCatalogEntry(
                groupId = tokens.take(2).joinToString("."),
                artifactId = tokens.drop(2).joinToString("-")
            )
        }
        if (tokens.size >= 2) {
            return ResolvedCatalogEntry(
                groupId = tokens.first(),
                artifactId = tokens.drop(1).joinToString("-")
            )
        }
        return ResolvedCatalogEntry(
            groupId = alias.replace('-', '.'),
            artifactId = alias
        )
    }

    private fun inferFromExistingGroups(alias: String, existingEntries: List<CatalogLibraryEntry>): ResolvedCatalogEntry? {
        return existingEntries
            .mapNotNull { entry ->
                val groupPrefix = toKebabCase(entry.groupId)
                if (groupPrefix.isBlank() || !alias.startsWith("$groupPrefix-")) {
                    return@mapNotNull null
                }
                val artifactId = alias.removePrefix("$groupPrefix-")
                if (artifactId.isBlank()) {
                    return@mapNotNull null
                }
                ResolvedCatalogEntry(
                    groupId = entry.groupId,
                    artifactId = artifactId,
                    score = groupPrefix.length
                )
            }
            .maxByOrNull { it.score }
    }

    private fun upsertLibraryEntry(
        originalText: String,
        alias: String,
        groupId: String,
        artifactId: String,
        versionKey: String
    ): String {
        val lines = if (originalText.isEmpty()) mutableListOf() else originalText.split('\n').toMutableList()
        val entryLine = """$alias = { group = "$groupId", name = "$artifactId", version.ref = "$versionKey" }"""
        val section = findSection(lines, "[libraries]")

        if (section != null) {
            val entryRegex = Regex("""^\s*${Regex.escape(alias)}\s*=""")
            val existingIndex = (section.start + 1 until section.end).firstOrNull { index ->
                entryRegex.containsMatchIn(lines[index])
            }
            if (existingIndex != null) {
                lines[existingIndex] = entryLine
                return lines.joinToString("\n")
            }
            val insertAt = trimTrailingBlanks(lines, section.start, section.end)
            lines.add(insertAt, entryLine)
            return lines.joinToString("\n")
        }

        if (lines.isNotEmpty() && lines.last().isNotBlank()) {
            lines.add("")
        }
        lines.add("[libraries]")
        lines.add(entryLine)
        return lines.joinToString("\n")
    }

    private fun upsertVersionEntry(
        originalText: String,
        versionKey: String,
        versionValue: String
    ): String {
        val lines = if (originalText.isEmpty()) mutableListOf() else originalText.split('\n').toMutableList()
        val entryLine = "$versionKey = \"$versionValue\""
        val section = findSection(lines, "[versions]")

        if (section != null) {
            val entryRegex = Regex("""^\s*${Regex.escape(versionKey)}\s*=""")
            val existingIndex = (section.start + 1 until section.end).firstOrNull { index ->
                entryRegex.containsMatchIn(lines[index])
            }
            if (existingIndex != null) {
                return lines.joinToString("\n")
            }
            val insertAt = trimTrailingBlanks(lines, section.start, section.end)
            lines.add(insertAt, entryLine)
            return lines.joinToString("\n")
        }

        if (lines.isNotEmpty() && lines.last().isNotBlank()) {
            lines.add("")
        }
        lines.add("[versions]")
        lines.add(entryLine)
        return lines.joinToString("\n")
    }

    private fun findSection(lines: List<String>, header: String): SectionRange? {
        val start = lines.indexOfFirst { it.trim() == header }
        if (start < 0) {
            return null
        }
        var end = lines.size
        for (index in start + 1 until lines.size) {
            val trimmed = lines[index].trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                end = index
                break
            }
        }
        return SectionRange(start, end)
    }

    private fun trimTrailingBlanks(lines: List<String>, sectionStart: Int, sectionEnd: Int): Int {
        var insertAt = sectionEnd
        while (insertAt > sectionStart + 1 && lines[insertAt - 1].isBlank()) {
            insertAt--
        }
        return insertAt
    }

    private fun findVersionValueOffset(text: String, versionKey: String): Int {
        val match = Regex("""(?m)^\s*${Regex.escape(versionKey)}\s*=.*$""").find(text) ?: return 0
        val lineText = match.value
        val versionMatch = Regex("=\\s*\"").find(lineText)
        return if (versionMatch != null) {
            match.range.first + versionMatch.range.last + 1
        } else {
            match.range.first
        }
    }

    private fun toKebabCase(value: String): String {
        return value
            .replace('.', '-')
            .replace('_', '-')
            .lowercase()
    }

    data class TargetCall(
        val alias: String
    )

    data class NavigationTarget(
        val virtualFile: VirtualFile,
        val offset: Int
    )

    private data class ResolvedCatalogEntry(
        val groupId: String,
        val artifactId: String,
        val versionValue: String? = null,
        val score: Int = 0
    )

    private data class CatalogLibraryEntry(
        val alias: String,
        val groupId: String,
        val artifactId: String,
        val versionRef: String?,
        val directVersion: String?,
        val lineText: String,
        val lineStartOffset: Int
    )

    private data class CatalogVersionEntry(
        val key: String,
        val value: String,
        val lineText: String,
        val lineStartOffset: Int
    )

    private data class SectionRange(
        val start: Int,
        val end: Int
    )

    private val FIND_LIBRARY_ALIAS_REGEX = Regex("""\b[A-Za-z_][A-Za-z0-9_]*\s*\.\s*findLibrary\s*\(\s*"([^"]+)"\s*\)\s*\.?\s*get\s*\(\s*\)""")

    private val DOMAIN_PREFIXES = setOf("com", "org", "io", "net", "dev", "app", "site", "cn", "top", "me")
}

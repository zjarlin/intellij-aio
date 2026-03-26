package site.addzero.gradle.buddy.notification

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import java.util.regex.Pattern

/**
 * Sorts and de-duplicates Version Catalog TOML files.
 */
class VersionCatalogSorter(private val project: Project) {

    fun organizeContent(content: String): String = sortContent(content)

    fun sort(file: VirtualFile) {
        val documentManager = FileDocumentManager.getInstance()
        val document = documentManager.getDocument(file)
        val content = document?.text ?: String(file.contentsToByteArray())
        val newContent = sortContent(content)

        if (content != newContent) {
            WriteCommandAction.runWriteCommandAction(project) {
                if (document != null) {
                    document.replaceString(0, document.textLength, newContent)
                    PsiDocumentManager.getInstance(project).commitDocument(document)
                    documentManager.saveDocument(document)
                } else {
                    file.setBinaryContent(newContent.toByteArray())
                }
            }
        }
    }

    private fun sortContent(content: String): String {
        val repairedContent = repairLibraryAliasAndVersionConflicts(content)
        val sections = parseSections(repairedContent)
        val sb = StringBuilder()
        val duplicates = mutableListOf<String>()

        // Collect all version definitions for resolving version.ref values
        val versionMap = buildVersionMap(repairedContent)

        sections.forEach { section ->
            val processed = processSection(section, duplicates, versionMap)
            if (processed.isNotEmpty()) {
                sb.append(processed)
            }
        }

        if (duplicates.isNotEmpty()) {
            sb.append(createGroupHeader("duplicates")).append("\n")
            duplicates.forEach { line ->
                sb.append(line.trimEnd()).append("\n")
            }
        }

        return sb.toString().trimEnd() + "\n"
    }

    /**
     * 在真正排序之前，先修复 [libraries] 中的 alias 冲突，以及和之关联的 version.ref。
     *
     * 处理目标：
     * 1. 同一个 alias / accessor 指向多个不同库时，自动为冲突项生成稳定的新 alias
     * 2. 完全相同坐标的重复 library，优先保留 version.ref 更规范的一条
     * 3. alias 变更后，若 version.ref 明显是 alias 派生键，则同步修正或补一份新版本键
     * 4. 若旧版本键已无任何引用，则自动删除
     */
    private fun repairLibraryAliasAndVersionConflicts(content: String): String {
        val lines = content.lines().toMutableList()
        val versionValues = buildVersionMap(content)
        val libraryEntries = parseIndexedLibraries(lines, versionValues)
        if (libraryEntries.isEmpty()) {
            return content
        }

        val collisionGroups = libraryEntries
            .groupBy { toGradleAccessorName(it.alias) }
            .filter { it.value.size > 1 }

        if (collisionGroups.isEmpty()) {
            return content
        }

        val versionEntriesByKey = parseIndexedVersions(lines).associateBy { it.key }.toMutableMap()
        val versionUsageCounts = countVersionRefUsages(lines).toMutableMap()
        val removedLibraryLines = mutableSetOf<Int>()
        val aliasUpdates = mutableMapOf<Int, String>()
        val versionRefUpdates = mutableMapOf<Int, String>()
        val versionKeyRenames = mutableMapOf<String, String>()
        val clonedVersionEntries = linkedMapOf<String, String>()
        val occupiedAliases = libraryEntries
            .filter { toGradleAccessorName(it.alias) !in collisionGroups.keys }
            .map { toGradleAccessorName(it.alias) }
            .toMutableSet()

        for ((_, rawGroup) in collisionGroups.toSortedMap()) {
            val dedupedGroup = rawGroup
                .groupBy { "${it.groupId}:${it.artifactId}:${it.resolvedVersion}" }
                .values
                .map { sameCoordinateEntries ->
                    val winner = selectPreferredDuplicateLibrary(sameCoordinateEntries)
                    sameCoordinateEntries.filter { it !== winner }.forEach { duplicate ->
                        removedLibraryLines += duplicate.lineIndex
                        duplicate.versionRef?.let { refKey ->
                            versionUsageCounts[refKey] = (versionUsageCounts[refKey] ?: 0) - 1
                        }
                    }
                    winner
                }
                .sortedWith(compareBy({ it.alias }, { it.groupId }, { it.artifactId }))

            if (dedupedGroup.isEmpty()) {
                continue
            }

            val baseAlias = chooseBaseAliasForCollisionGroup(dedupedGroup)
            val primary = selectPrimaryAliasOwner(baseAlias, dedupedGroup)
            val currentAliasesInGroup = dedupedGroup.map { it.alias }.toSet()
            val baseVersionKey = dedupedGroup.firstOrNull { it.versionRef == baseAlias }?.versionRef
            val sameResolvedVersion = dedupedGroup.map { it.resolvedVersion }.filter { it.isNotBlank() }.distinct().size == 1

            occupiedAliases += toGradleAccessorName(baseAlias)
            if (primary.alias != baseAlias) {
                aliasUpdates[primary.lineIndex] = baseAlias
            }

            for (entry in dedupedGroup) {
                if (entry === primary) {
                    continue
                }
                val newAlias = generateUniqueAliasForCollision(
                    entry = entry,
                    baseAlias = baseAlias,
                    occupiedAliases = occupiedAliases
                )
                aliasUpdates[entry.lineIndex] = newAlias
                occupiedAliases += toGradleAccessorName(newAlias)
            }

            for (entry in dedupedGroup) {
                val oldRef = entry.versionRef ?: continue
                val newAlias = aliasUpdates[entry.lineIndex]
                    ?: if (entry === primary) baseAlias else entry.alias

                val shouldFollowAlias =
                    oldRef == entry.alias ||
                        oldRef == baseAlias ||
                        oldRef in currentAliasesInGroup

                val desiredRef = when {
                    shouldFollowAlias && oldRef != newAlias -> newAlias
                    sameResolvedVersion &&
                        baseVersionKey != null &&
                        oldRef != baseVersionKey &&
                        (versionUsageCounts[oldRef] ?: 0) <= 1 -> baseVersionKey
                    else -> null
                } ?: continue

                if (desiredRef == oldRef) {
                    continue
                }

                val oldVersionValue = versionEntriesByKey[oldRef]?.value ?: continue
                versionRefUpdates[entry.lineIndex] = desiredRef
                versionUsageCounts[oldRef] = (versionUsageCounts[oldRef] ?: 0) - 1
                versionUsageCounts[desiredRef] = (versionUsageCounts[desiredRef] ?: 0) + 1

                if (versionEntriesByKey.containsKey(desiredRef) || clonedVersionEntries.containsKey(desiredRef)) {
                    continue
                }

                if ((versionUsageCounts[oldRef] ?: 0) <= 0 && versionKeyRenames[oldRef] == null) {
                    versionKeyRenames[oldRef] = desiredRef
                    val oldEntry = versionEntriesByKey.remove(oldRef)
                    if (oldEntry != null) {
                        versionEntriesByKey[desiredRef] = oldEntry.copy(key = desiredRef)
                    }
                } else {
                    clonedVersionEntries[desiredRef] = oldVersionValue
                    versionEntriesByKey[desiredRef] = IndexedVersionEntry(
                        key = desiredRef,
                        value = oldVersionValue,
                        lineIndex = -1
                    )
                }
            }
        }

        for ((lineIndex, _) in aliasUpdates.toSortedMap()) {
            lines[lineIndex] = rewriteLibraryAlias(lines[lineIndex], aliasUpdates.getValue(lineIndex))
        }
        for ((lineIndex, _) in versionRefUpdates.toSortedMap()) {
            lines[lineIndex] = rewriteLibraryVersionRef(lines[lineIndex], versionRefUpdates.getValue(lineIndex))
        }

        for (lineIndex in removedLibraryLines.sortedDescending()) {
            lines.removeAt(lineIndex)
        }

        if (versionKeyRenames.isNotEmpty()) {
            for ((oldKey, newKey) in versionKeyRenames) {
                val versionEntry = parseIndexedVersions(lines).firstOrNull { it.key == oldKey } ?: continue
                lines[versionEntry.lineIndex] = rewriteVersionKey(lines[versionEntry.lineIndex], newKey)
            }
        }

        val finalUsageCounts = countVersionRefUsages(lines)
        val removableVersionLines = parseIndexedVersions(lines)
            .filter { (finalUsageCounts[it.key] ?: 0) <= 0 }
            .map { it.lineIndex }
            .sortedDescending()

        for (lineIndex in removableVersionLines) {
            lines.removeAt(lineIndex)
        }

        if (clonedVersionEntries.isNotEmpty()) {
            insertVersionEntries(lines, clonedVersionEntries)
        }

        return lines.joinToString("\n")
    }

    /**
     * Build a map of version variable name -> version value from [versions] section.
     */
    private fun buildVersionMap(content: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val lines = content.lines()
        var inVersions = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.matches(Regex("^\\[versions\\]\\s*(#.*)?$"))) {
                inVersions = true
                continue
            }
            if (trimmed.matches(Regex("^\\[.+\\]\\s*(#.*)?$"))) {
                inVersions = false
                continue
            }
            if (inVersions && trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                val key = trimmed.substringBefore("=").trim()
                val value = trimmed.substringAfter("=").trim().removeSurrounding("\"")
                if (key.isNotEmpty()) {
                    map[key] = value
                }
            }
        }
        return map
    }

    private fun parseSections(content: String): List<Section> {
        val lines = content.lines()
        val sections = mutableListOf<Section>()
        var currentSectionName = ""
        var currentLines = mutableListOf<String>()

        val sectionHeaderPattern = Pattern.compile("^\\[(.+)\\]\\s*(#.*)?$")

        for (line in lines) {
            val trimLine = line.trim()
            val matcher = sectionHeaderPattern.matcher(trimLine)
            if (matcher.matches()) {
                if (currentLines.isNotEmpty() || currentSectionName.isNotEmpty()) {
                    sections.add(Section(currentSectionName, currentLines))
                }
                currentSectionName = matcher.group(1)
                currentLines = mutableListOf()
                currentLines.add(line) // Add the header line
            } else {
                currentLines.add(line)
            }
        }
        if (currentLines.isNotEmpty() || currentSectionName.isNotEmpty()) {
            sections.add(Section(currentSectionName, currentLines))
        }

        return sections
    }

    private fun processSection(section: Section, duplicates: MutableList<String>, versionMap: Map<String, String>): String {
        if (section.lines.isEmpty()) return ""

        if (section.name.isEmpty()) {
            return section.lines.joinToString("\n") { it }.trim().let { if (it.isEmpty()) "" else "$it\n" }
        }

        return when (section.name) {
            "bundles" -> processBundlesSection(section, duplicates)
            "libraries" -> processLibrariesSection(section, duplicates, versionMap)
            "versions", "plugins" -> processKeyValueSection(section, duplicates)
            else -> processKeyValueSection(section, duplicates)
        }
    }

    // ======================== [bundles] handling ========================

    /**
     * Parse bundles correctly: each bundle entry can span multiple lines
     * (multi-line array values). We must keep multi-line entries together as one unit.
     */
    private fun processBundlesSection(section: Section, duplicates: MutableList<String>): String {
        val header = section.lines.first()
        val entries = parseBundleEntries(section.lines.subList(1, section.lines.size))

        val seen = LinkedHashMap<String, BundleEntry>()
        entries.forEach { entry ->
            val key = entry.key
            if (seen.containsKey(key)) {
                duplicates.add("# DUPLICATE [bundles]: ${entry.allLines.first().trim()}")
            } else {
                seen[key] = entry
            }
        }

        val sorted = seen.values.sortedBy { it.key }

        val sb = StringBuilder()
        sb.append(header).append("\n")
        sorted.forEach { entry ->
            entry.comments.forEach { sb.append(it.trimEnd()).append("\n") }
            entry.allLines.forEach { sb.append(it.trimEnd()).append("\n") }
        }

        val cleaned = sb.lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")

        return if (cleaned.isEmpty()) "" else "$cleaned\n"
    }

    private fun parseBundleEntries(lines: List<String>): List<BundleEntry> {
        val entries = mutableListOf<BundleEntry>()
        val comments = mutableListOf<String>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            when {
                trimmed.isBlank() -> {
                    comments.add(line)
                    i++
                }
                GROUP_HEADER_PATTERN.matches(trimmed) -> {
                    comments.clear()
                    i++
                }
                trimmed.startsWith("#") -> {
                    comments.add(line)
                    i++
                }
                else -> {
                    // Start of an entry. Collect all lines until the entry is complete.
                    val entryLines = mutableListOf<String>()
                    entryLines.add(line)

                    // Check if this line contains an opening '[' for array value
                    // and doesn't have a closing ']' on the same line
                    val valueAfterEquals = line.substringAfter("=", "")
                    val openBrackets = valueAfterEquals.count { it == '[' }
                    val closeBrackets = valueAfterEquals.count { it == ']' }
                    var bracketDepth = openBrackets - closeBrackets

                    i++
                    // Continue collecting lines if we're inside an unclosed bracket
                    while (i < lines.size && bracketDepth > 0) {
                        val contLine = lines[i]
                        entryLines.add(contLine)
                        bracketDepth += contLine.count { it == '[' }
                        bracketDepth -= contLine.count { it == ']' }
                        i++
                    }

                    val key = extractKey(entryLines.first())
                    entries.add(BundleEntry(key, entryLines, comments.toList()))
                    comments.clear()
                }
            }
        }
        return entries
    }

    // ======================== [libraries] handling ========================

    private fun processLibrariesSection(section: Section, duplicates: MutableList<String>, versionMap: Map<String, String>): String {
        val header = section.lines.first()
        val rawEntries = parseSimpleEntries(section.lines.subList(1, section.lines.size))

        val parsed = rawEntries.map { entry ->
            val key = extractKey(entry.line)
            val group = extractGroup(entry.line)
            val name = extractName(entry.line)
            val module = extractModule(entry.line)

            val fullGroup = group ?: module?.substringBefore(":") ?: ""
            val fullName = name ?: module?.substringAfter(":") ?: ""
            val resolvedVersion = resolveVersion(entry.line, versionMap)

            ParsedLibrary(entry, key, fullGroup, fullName, resolvedVersion)
        }

        val grouped = parsed.groupBy { it.group.ifEmpty { "~" } }
        // For duplicate detection: group:name:resolvedVersion -> first occurrence
        val seen = mutableSetOf<String>()
        val result = mutableListOf<RawEntry>()
        val singleGroupEntries = mutableListOf<RawEntry>()

        grouped.toSortedMap().forEach { (groupKey, groupEntries) ->
            val normalizedGroup = groupKey.takeIf { it != "~" }
            val sortedEntries = groupEntries.sortedWith(compareBy({ it.name }, { it.key }))

            sortedEntries.forEachIndexed { index, item ->
                // Only consider as duplicate if group + artifact + resolved version are ALL the same
                val identifier = if (item.group.isNotEmpty() && item.name.isNotEmpty()) {
                    "${item.group}:${item.name}:${item.resolvedVersion}"
                } else null

                if (identifier != null && !seen.add(identifier)) {
                    duplicates.add("# DUPLICATE [libraries]: ${item.entry.line.trim()}")
                    return@forEachIndexed
                }

                val isSingleGroup = normalizedGroup != null && sortedEntries.size == 1
                if (isSingleGroup) {
                    singleGroupEntries.add(item.entry)
                } else {
                    if (normalizedGroup != null && index == 0) {
                        result.add(RawEntry(createGroupHeader(normalizedGroup), emptyList()))
                    }
                    result.add(item.entry)
                }
            }
        }

        if (singleGroupEntries.isNotEmpty()) {
            result.add(RawEntry(createGroupHeader("single group"), emptyList()))
            result.addAll(singleGroupEntries.sortedBy { extractKey(it.line) })
        }

        val sb = StringBuilder()
        sb.append(header).append("\n")
        result.forEach { entry ->
            entry.comments.forEach { sb.append(it.trimEnd()).append("\n") }
            sb.append(entry.line.trimEnd()).append("\n")
        }

        val cleaned = sb.lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")

        return if (cleaned.isEmpty()) "" else "$cleaned\n"
    }

    /**
     * Resolve the actual version value for a library line.
     * Handles: version.ref = "key", version = "1.0.0", and short format "group:name:version"
     */
    private fun resolveVersion(line: String, versionMap: Map<String, String>): String {
        // version.ref = "someKey"
        val versionRefMatch = Regex("""version\.ref\s*=\s*"([^"]+)"""").find(line)
        if (versionRefMatch != null) {
            val refKey = versionRefMatch.groupValues[1]
            return versionMap[refKey] ?: refKey // fallback to ref key if not found
        }
        // version = "1.0.0" (direct version, but not version.ref)
        val directVersionMatch = Regex("""(?<!\.)\bversion\s*=\s*"([^"]+)"""").find(line)
        if (directVersionMatch != null) {
            return directVersionMatch.groupValues[1]
        }
        // Short format: key = "group:name:version"
        val shortMatch = Regex("""=\s*"([^":]+):([^":]+):([^"]+)"""").find(line)
        if (shortMatch != null) {
            return shortMatch.groupValues[3]
        }
        return ""
    }

    // ======================== [versions] / [plugins] handling ========================

    private fun processKeyValueSection(section: Section, duplicates: MutableList<String>): String {
        val header = section.lines.first()
        val rawEntries = parseSimpleEntries(section.lines.subList(1, section.lines.size))

        val seen = LinkedHashMap<String, RawEntry>()
        // Also track Gradle-normalized accessor names to detect clashes like
        // navigation-compose vs navigationCompose (both → "navigationcompose")
        val seenAccessors = mutableMapOf<String, String>() // normalizedName → first key
        rawEntries.forEach { entry ->
            val key = extractKey(entry.line)
            val accessor = toGradleAccessorName(key)
            val existingKey = seenAccessors[accessor]
            if (seen.containsKey(key)) {
                duplicates.add("# DUPLICATE [${section.name}]: ${entry.line.trim()}")
            } else if (existingKey != null && existingKey != key) {
                // Gradle accessor name clash — treat as duplicate
                duplicates.add("# DUPLICATE (accessor clash with '$existingKey') [${section.name}]: ${entry.line.trim()}")
            } else {
                seen[key] = entry
                seenAccessors[accessor] = key
            }
        }
        val sorted = seen.values.sortedBy { extractKey(it.line) }

        val sb = StringBuilder()
        sb.append(header).append("\n")
        sorted.forEach { entry ->
            entry.comments.forEach { sb.append(it.trimEnd()).append("\n") }
            sb.append(entry.line.trimEnd()).append("\n")
        }

        val cleaned = sb.lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")

        return if (cleaned.isEmpty()) "" else "$cleaned\n"
    }

    // ======================== Common parsing ========================

    /**
     * Parse single-line entries (for [versions], [libraries], [plugins]).
     * Each entry is one line with optional preceding comment lines.
     */
    private fun parseSimpleEntries(lines: List<String>): List<RawEntry> {
        val entries = mutableListOf<RawEntry>()
        val comments = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.isBlank() -> comments.add(line)
                GROUP_HEADER_PATTERN.matches(trimmed) -> comments.clear()
                trimmed.startsWith("#") -> comments.add(line)
                else -> {
                    entries.add(RawEntry(line, comments.toList()))
                    comments.clear()
                }
            }
        }
        return entries
    }

    // ======================== Field extractors ========================

    private fun extractKey(line: String): String = line.substringBefore("=").trim()

    private fun extractGroup(line: String): String? {
        val match = Regex("group\\s*=\\s*\"([^\"]+)\"").find(line)
        return match?.groupValues?.get(1)
    }

    private fun extractName(line: String): String? {
        val match = Regex("name\\s*=\\s*\"([^\"]+)\"").find(line)
        return match?.groupValues?.get(1)
    }

    private fun extractModule(line: String): String? {
        val match = Regex("module\\s*=\\s*\"([^\"]+)\"").find(line)
        return match?.groupValues?.get(1)
    }

    private fun parseIndexedLibraries(lines: List<String>, versionMap: Map<String, String>): List<IndexedLibraryEntry> {
        val result = mutableListOf<IndexedLibraryEntry>()
        var inLibraries = false

        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.matches(Regex("^\\[.+\\]\\s*(#.*)?$"))) {
                inLibraries = trimmed.matches(Regex("^\\[libraries\\]\\s*(#.*)?$"))
                continue
            }
            if (!inLibraries || trimmed.isBlank() || trimmed.startsWith("#")) {
                continue
            }

            val parsed = parseLibraryLine(trimmed) ?: continue
            result += IndexedLibraryEntry(
                lineIndex = index,
                alias = parsed.alias,
                groupId = parsed.groupId,
                artifactId = parsed.artifactId,
                versionRef = parsed.versionRef,
                directVersion = parsed.directVersion,
                resolvedVersion = parsed.versionRef?.let { versionMap[it] } ?: parsed.directVersion.orEmpty()
            )
        }

        return result
    }

    private fun parseIndexedVersions(lines: List<String>): List<IndexedVersionEntry> {
        val result = mutableListOf<IndexedVersionEntry>()
        var inVersions = false

        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.matches(Regex("^\\[.+\\]\\s*(#.*)?$"))) {
                inVersions = trimmed.matches(Regex("^\\[versions\\]\\s*(#.*)?$"))
                continue
            }
            if (!inVersions || trimmed.isBlank() || trimmed.startsWith("#")) {
                continue
            }

            val key = trimmed.substringBefore("=").trim()
            val valueMatch = Regex("""=\s*"([^"]+)"""").find(trimmed) ?: continue
            result += IndexedVersionEntry(
                key = key,
                value = valueMatch.groupValues[1],
                lineIndex = index
            )
        }

        return result
    }

    private fun parseLibraryLine(line: String): ParsedLibraryLine? {
        val alias = line.substringBefore("=").trim()
        if (alias.isEmpty() || alias.startsWith("#")) {
            return null
        }

        val groupId: String
        val artifactId: String

        val moduleMatch = Regex("""module\s*=\s*"([^":]+):([^"]+)"""").find(line)
        if (moduleMatch != null) {
            groupId = moduleMatch.groupValues[1]
            artifactId = moduleMatch.groupValues[2]
        } else {
            val groupMatch = Regex("""group\s*=\s*"([^"]+)"""").find(line)
            val nameMatch = Regex("""\bname\s*=\s*"([^"]+)"""").find(line)
            if (groupMatch != null && nameMatch != null) {
                groupId = groupMatch.groupValues[1]
                artifactId = nameMatch.groupValues[1]
            } else {
                val shortMatch = Regex("""=\s*"([^":]+):([^":]+):([^"]+)"""").find(line)
                if (shortMatch != null) {
                    groupId = shortMatch.groupValues[1]
                    artifactId = shortMatch.groupValues[2]
                } else {
                    return null
                }
            }
        }

        val versionRef = Regex("""version\.ref\s*=\s*"([^"]+)"""").find(line)?.groupValues?.get(1)
        val directVersion = Regex("""(?<!\.)\bversion\s*=\s*"([^"]+)"""").find(line)?.groupValues?.get(1)

        return ParsedLibraryLine(
            alias = alias,
            groupId = groupId,
            artifactId = artifactId,
            versionRef = versionRef,
            directVersion = directVersion
        )
    }

    private fun toKebabCase(value: String): String {
        return value.replace('.', '-')
            .replace('_', '-')
            .replace(Regex("([a-z])([A-Z])"), "$1-$2")
            .lowercase()
    }

    private fun countVersionRefUsages(lines: List<String>): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        for (line in lines) {
            val ref = Regex("""version\.ref\s*=\s*"([^"]+)"""").find(line)?.groupValues?.get(1) ?: continue
            counts[ref] = (counts[ref] ?: 0) + 1
        }
        return counts
    }

    private fun selectPreferredDuplicateLibrary(entries: List<IndexedLibraryEntry>): IndexedLibraryEntry {
        return entries.maxWithOrNull(
            compareBy<IndexedLibraryEntry> { duplicate ->
                val aliasLikeScore = when {
                    duplicate.versionRef == duplicate.alias -> 3
                    duplicate.versionRef == "${toKebabCase(duplicate.groupId)}-${toKebabCase(duplicate.artifactId)}" -> 2
                    duplicate.versionRef != null -> 1
                    else -> 0
                }
                aliasLikeScore
            }.thenByDescending { duplicate ->
                duplicate.versionRef == duplicate.alias
            }.thenBy { duplicate ->
                duplicate.lineIndex
            }
        ) ?: entries.first()
    }

    private fun chooseBaseAliasForCollisionGroup(entries: List<IndexedLibraryEntry>): String {
        val best = entries.maxWithOrNull(
            compareBy<IndexedLibraryEntry> { entry ->
                val canonicalAlias = buildCanonicalAlias(entry)
                when {
                    entry.alias == canonicalAlias -> 3
                    entry.alias == buildCompactAlias(entry) -> 2
                    else -> 1
                }
            }.thenByDescending { entry ->
                entry.alias.count { it == '-' }
            }.thenBy { entry ->
                entry.lineIndex
            }
        ) ?: entries.first()

        return when {
            best.alias == buildCanonicalAlias(best) -> best.alias
            best.alias == buildCompactAlias(best) -> best.alias
            else -> buildCanonicalAlias(best)
        }
    }

    private fun selectPrimaryAliasOwner(
        baseAlias: String,
        entries: List<IndexedLibraryEntry>
    ): IndexedLibraryEntry {
        return entries.maxWithOrNull(
            compareBy<IndexedLibraryEntry> { entry ->
                when {
                    entry.alias == baseAlias -> 4
                    buildCanonicalAlias(entry) == baseAlias -> 3
                    buildCompactAlias(entry) == baseAlias -> 2
                    else -> 1
                }
            }.thenByDescending { entry ->
                entry.artifactId.length
            }.thenBy { entry ->
                entry.lineIndex
            }
        ) ?: entries.first()
    }

    private fun generateUniqueAliasForCollision(
        entry: IndexedLibraryEntry,
        baseAlias: String,
        occupiedAliases: MutableSet<String>
    ): String {
        val groupKebab = toKebabCase(entry.groupId)
        val artifactKebab = toKebabCase(entry.artifactId)
        val canonicalAlias = buildCanonicalAlias(entry)
        val candidates = linkedSetOf<String>()

        if (artifactKebab.startsWith(groupKebab)) {
            candidates += "$baseAlias-$artifactKebab"
        }
        candidates += canonicalAlias
        candidates += "$baseAlias-$artifactKebab"
        candidates += buildCompactAlias(entry)

        for (candidate in candidates) {
            val accessor = toGradleAccessorName(candidate)
            if (accessor !in occupiedAliases) {
                return candidate
            }
        }

        var suffix = 2
        while (true) {
            val fallback = "$canonicalAlias-alt$suffix"
            val accessor = toGradleAccessorName(fallback)
            if (accessor !in occupiedAliases) {
                return fallback
            }
            suffix++
        }
    }

    private fun buildCanonicalAlias(entry: IndexedLibraryEntry): String {
        return "${toKebabCase(entry.groupId)}-${toKebabCase(entry.artifactId)}"
    }

    private fun buildCompactAlias(entry: IndexedLibraryEntry): String {
        val groupKebab = toKebabCase(entry.groupId)
        val artifactKebab = toKebabCase(entry.artifactId)
        return if (artifactKebab.startsWith(groupKebab)) {
            "$groupKebab-${artifactKebab.substringAfter(groupKebab).trimStart('-')}".trimEnd('-')
        } else {
            buildCanonicalAlias(entry)
        }
    }

    private fun rewriteLibraryAlias(line: String, newAlias: String): String {
        val oldAlias = line.substringBefore("=").trim()
        return line.replaceFirst(oldAlias, newAlias)
    }

    private fun rewriteLibraryVersionRef(line: String, newVersionRef: String): String {
        return line.replace(
            Regex("""version\.ref\s*=\s*"([^"]+)""""),
            """version.ref = "$newVersionRef""""
        )
    }

    private fun rewriteVersionKey(line: String, newKey: String): String {
        val oldKey = line.substringBefore("=").trim()
        return line.replaceFirst(oldKey, newKey)
    }

    private fun insertVersionEntries(lines: MutableList<String>, newEntries: Map<String, String>) {
        if (newEntries.isEmpty()) {
            return
        }

        var versionsHeaderIndex = -1
        var insertIndex = lines.size
        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.matches(Regex("^\\[versions\\]\\s*(#.*)?$"))) {
                versionsHeaderIndex = index
                insertIndex = index + 1
                continue
            }
            if (versionsHeaderIndex >= 0 && index > versionsHeaderIndex && trimmed.matches(Regex("^\\[.+\\]\\s*(#.*)?$"))) {
                insertIndex = index
                break
            }
            if (versionsHeaderIndex >= 0) {
                insertIndex = index + 1
            }
        }

        if (versionsHeaderIndex < 0) {
            val block = buildList {
                add("[versions]")
                newEntries.toSortedMap().forEach { (key, value) ->
                    add("""$key = "$value"""")
                }
            }
            if (lines.isNotEmpty() && lines.last().isNotBlank()) {
                lines.add("")
            }
            lines.addAll(block)
            return
        }

        val block = newEntries.toSortedMap().map { (key, value) ->
            """$key = "$value""""
        }
        lines.addAll(insertIndex, block)
    }

    private fun createGroupHeader(group: String): String {
        val targetWidth = 70
        val label = " $group "
        val hyphenSpace = (targetWidth - label.length).coerceAtLeast(2)
        val left = hyphenSpace / 2
        val right = hyphenSpace - left
        return "#${"-".repeat(left)}$label${"-".repeat(right)}"
    }

    companion object {
        private val GROUP_HEADER_PATTERN = Regex("^#-+\\s.+\\s-+$")
    }

    /**
     * Compute the Gradle accessor name for a TOML key.
     * Gradle treats `-`, `_`, `.` and camelCase boundaries as equivalent separators.
     */
    private fun toGradleAccessorName(key: String): String {
        return key
            .replace(Regex("([a-z0-9])([A-Z])"), "$1-$2")
            .split(Regex("[\\-_.]"))
            .joinToString("") { it.lowercase() }
    }

    data class Section(val name: String, val lines: List<String>)
    data class RawEntry(val line: String, val comments: List<String>)
    data class BundleEntry(val key: String, val allLines: List<String>, val comments: List<String>)
    data class ParsedLibrary(val entry: RawEntry, val key: String, val group: String, val name: String, val resolvedVersion: String)
    data class ParsedLibraryLine(
        val alias: String,
        val groupId: String,
        val artifactId: String,
        val versionRef: String?,
        val directVersion: String?
    )
    data class IndexedLibraryEntry(
        val lineIndex: Int,
        val alias: String,
        val groupId: String,
        val artifactId: String,
        val versionRef: String?,
        val directVersion: String?,
        val resolvedVersion: String
    )
    data class IndexedVersionEntry(
        val key: String,
        val value: String,
        val lineIndex: Int
    )
}

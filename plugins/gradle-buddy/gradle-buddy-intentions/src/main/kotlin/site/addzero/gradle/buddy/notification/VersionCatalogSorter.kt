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
        val sections = parseSections(content)
        val sb = StringBuilder()
        val duplicates = mutableListOf<String>()

        // Collect all version definitions for resolving version.ref values
        val versionMap = buildVersionMap(content)

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
        rawEntries.forEach { entry ->
            val key = extractKey(entry.line)
            if (seen.containsKey(key)) {
                duplicates.add("# DUPLICATE [${section.name}]: ${entry.line.trim()}")
            } else {
                seen[key] = entry
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

    data class Section(val name: String, val lines: List<String>)
    data class RawEntry(val line: String, val comments: List<String>)
    data class BundleEntry(val key: String, val allLines: List<String>, val comments: List<String>)
    data class ParsedLibrary(val entry: RawEntry, val key: String, val group: String, val name: String, val resolvedVersion: String)
}

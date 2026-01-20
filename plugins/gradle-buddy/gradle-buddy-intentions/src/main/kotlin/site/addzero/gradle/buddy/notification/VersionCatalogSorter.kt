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

        sections.forEach { section ->
            sb.append(processSection(section))
        }

        return sb.toString()
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

    private fun processSection(section: Section): String {
        if (section.lines.isEmpty()) return ""

        // Handle empty/top-level section or unknown sections without sorting logic
        if (section.name.isEmpty()) {
            return section.lines.joinToString("\n") { it } + (if (section.lines.isNotEmpty()) "\n" else "")
        }

        val header = section.lines.first()
        val rawEntries = mutableListOf<RawEntry>()
        val comments = mutableListOf<String>()

        // Simple state machine to group comments with entries
        for (i in 1 until section.lines.size) {
            val line = section.lines[i]
            val trimmed = line.trim()
            if (trimmed.isBlank()) {
                // If we have accumulated comments but no entry, attach them to the next entry or flush if end
                 if (comments.isNotEmpty()) {
                     // For simplicity, empty lines reset comment blocks unless attached to an entry immediately
                     // But we want to preserve spacing.
                     // Strategy: Treat blank lines as part of "comments" block for the next entry
                 }
                comments.add(line)
            } else if (GROUP_HEADER_PATTERN.matches(trimmed)) {
                comments.clear()
                continue
            } else if (trimmed.startsWith("#")) {
                comments.add(line)
            } else {
                // It's likely an entry
                rawEntries.add(RawEntry(line, comments.toList()))
                comments.clear()
            }
        }

        // Remaining comments (trailing)
        val trailingComments = comments.toList()

        val processedEntries = when (section.name) {
            "versions" -> sortVersions(rawEntries)
            "libraries" -> sortLibraries(rawEntries)
            "plugins" -> sortPlugins(rawEntries)
            "bundles" -> sortBundles(rawEntries)
            else -> rawEntries // Return as is for unknown sections
        }

        val sb = StringBuilder()
        sb.append(header).append("\n")

        processedEntries.forEach { entry ->
            entry.comments.forEach { sb.append(it).append("\n") }
            sb.append(entry.line).append("\n")
        }

        trailingComments.forEach { sb.append(it).append("\n") }

        // Remove the extra newline at the very end if it was added by joinToString logic logic
        // But here we are building manually.
        // We need to ensure we don't double up newlines between sections if the original file had them at the end of sections.
        // The parsing logic included all lines.

        return sb.toString().replace(Regex("\\n+$"), "\n") // Ensure exactly one newline at end of section string
    }

    private fun sortVersions(entries: List<RawEntry>): List<RawEntry> {
        return sortByKey(entries)
    }

    private fun sortPlugins(entries: List<RawEntry>): List<RawEntry> {
        return sortByKey(entries)
    }

    private fun sortBundles(entries: List<RawEntry>): List<RawEntry> {
        return sortByKey(entries)
    }

    private fun sortLibraries(entries: List<RawEntry>): List<RawEntry> {
        val parsed = entries.map { entry ->
            val key = extractKey(entry.line)
            val group = extractGroup(entry.line)
            val name = extractName(entry.line)
            val module = extractModule(entry.line)

            // Prioritize module if available (group:name), otherwise try individual group and name
            val fullGroup = group ?: module?.substringBefore(":") ?: ""
            val fullName = name ?: module?.substringAfter(":") ?: ""

            ParsedLibrary(entry, key, fullGroup, fullName)
        }

        val grouped = parsed.groupBy { it.group.ifEmpty { "~" } }
        val seen = mutableSetOf<String>()
        val result = mutableListOf<RawEntry>()
        val duplicates = mutableListOf<RawEntry>()

        grouped.toSortedMap().forEach { (groupKey, groupEntries) ->
            val normalizedGroup = groupKey.takeIf { it != "~" }
            val sortedEntries = groupEntries.sortedWith(compareBy({ it.name }, { it.key }))

            val shouldPrintHeader = normalizedGroup != null && sortedEntries.size > 1
            if (shouldPrintHeader && normalizedGroup != null) {
                result.add(RawEntry(createGroupHeader(normalizedGroup), emptyList()))
            }

            sortedEntries.forEach { item ->
                val identifier = if (item.group.isNotEmpty() && item.name.isNotEmpty()) {
                    "${item.group}:${item.name}"
                } else null

                if (identifier != null && !seen.add(identifier)) {
                    duplicates.add(item.entry.copy(line = "# DUPLICATE: ${item.entry.line}"))
                } else {
                    result.add(item.entry)
                }
            }
        }

        if (duplicates.isNotEmpty()) {
            result.add(RawEntry(createGroupHeader("duplicates"), emptyList()))
            result.addAll(duplicates)
        }

        return result
    }

    private fun sortByKey(entries: List<RawEntry>): List<RawEntry> {
        return entries.sortedBy { extractKey(it.line) }
    }

    private fun extractKey(line: String): String {
        return line.substringBefore("=").trim()
    }

    // group = "..."
    private fun extractGroup(line: String): String? {
        val match = Regex("group\\s*=\\s*\"([^\"]+)\"").find(line)
        return match?.groupValues?.get(1)
    }

    // name = "..."
    private fun extractName(line: String): String? {
        val match = Regex("name\\s*=\\s*\"([^\"]+)\"").find(line)
        return match?.groupValues?.get(1)
    }

    // module = "group:name"
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
    data class ParsedLibrary(val entry: RawEntry, val key: String, val group: String, val name: String)
}

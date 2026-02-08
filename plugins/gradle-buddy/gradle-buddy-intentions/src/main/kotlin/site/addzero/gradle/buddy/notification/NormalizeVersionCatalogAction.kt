package site.addzero.gradle.buddy.notification

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager

/**
 * Normalize version catalog: ensure all library aliases and version.ref
 * follow the convention of using artifactId in kebab-case.
 *
 * Rules:
 * - Library alias = artifactId in kebab-case (dots/underscores -> hyphens)
 * - version.ref = artifactId in kebab-case
 * - [versions] key = artifactId in kebab-case
 * - Renames version variables and updates all references
 */
class NormalizeVersionCatalogAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        normalize(project, file)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    private fun normalize(project: Project, file: VirtualFile) {
        val documentManager = FileDocumentManager.getInstance()
        val document = documentManager.getDocument(file)
        val content = document?.text ?: String(file.contentsToByteArray())
        val newContent = normalizeContent(content)

        if (content != newContent) {
            WriteCommandAction.runWriteCommandAction(project, "Normalize Version Catalog", null, {
                if (document != null) {
                    document.replaceString(0, document.textLength, newContent)
                    PsiDocumentManager.getInstance(project).commitDocument(document)
                    documentManager.saveDocument(document)
                } else {
                    file.setBinaryContent(newContent.toByteArray())
                }
            })
        }
    }

    private fun normalizeContent(content: String): String {
        // Step 1: Parse all libraries to build rename maps
        val lines = content.lines().toMutableList()

        // Collect version renames: oldVersionKey -> newVersionKey (based on artifactId)
        val versionKeyRenames = mutableMapOf<String, String>()
        // Collect alias renames: oldAlias -> newAlias
        val aliasRenames = mutableMapOf<String, String>()

        // First pass: scan [libraries] to determine renames
        var inLibraries = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.matches(Regex("^\\[libraries]\\s*(#.*)?$"))) {
                inLibraries = true
                continue
            }
            if (trimmed.matches(Regex("^\\[.+]\\s*(#.*)?$"))) {
                inLibraries = false
                continue
            }
            if (!inLibraries || trimmed.isBlank() || trimmed.startsWith("#")) continue

            val info = parseLibraryLine(trimmed) ?: continue
            val normalizedArtifact = toKebabCase(info.artifactId)

            // Alias rename
            if (info.alias != normalizedArtifact) {
                aliasRenames[info.alias] = normalizedArtifact
            }

            // Version ref rename
            if (info.versionRef != null && info.versionRef != normalizedArtifact) {
                versionKeyRenames[info.versionRef] = normalizedArtifact
            }
        }

        // Handle conflicts: if multiple libraries map to the same normalized alias,
        // use group-artifactId format for disambiguation
        val aliasTargetCounts = mutableMapOf<String, MutableList<String>>()
        // Include existing aliases that don't need renaming
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.matches(Regex("^\\[libraries]\\s*(#.*)?$"))) { inLibraries = true; continue }
            if (trimmed.matches(Regex("^\\[.+]\\s*(#.*)?$"))) { inLibraries = false; continue }
            if (!inLibraries || trimmed.isBlank() || trimmed.startsWith("#")) continue
            val info = parseLibraryLine(trimmed) ?: continue
            val target = aliasRenames[info.alias] ?: info.alias
            aliasTargetCounts.getOrPut(target) { mutableListOf() }.add(info.alias)
        }
        // Disambiguate conflicts
        for ((target, sources) in aliasTargetCounts) {
            if (sources.size > 1) {
                // Multiple libraries map to same alias — use groupId prefix for all
                for (oldAlias in sources) {
                    val info = findLibraryByAlias(lines, oldAlias) ?: continue
                    val disambiguated = toKebabCase(info.groupId.substringAfterLast(".")) + "-" + toKebabCase(info.artifactId)
                    aliasRenames[oldAlias] = disambiguated
                    // Also update version ref target
                    if (info.versionRef != null) {
                        versionKeyRenames[info.versionRef] = disambiguated
                    }
                }
            }
        }

        // Same for version key conflicts
        val versionTargetCounts = mutableMapOf<String, MutableList<String>>()
        for ((old, target) in versionKeyRenames) {
            versionTargetCounts.getOrPut(target) { mutableListOf() }.add(old)
        }
        for ((target, sources) in versionTargetCounts) {
            if (sources.size > 1) {
                // Keep original version keys if they'd conflict
                for (old in sources) {
                    versionKeyRenames.remove(old)
                }
            }
        }

        // Step 2: Apply renames
        var result = content

        // Rename version keys in [versions] section
        for ((oldKey, newKey) in versionKeyRenames) {
            if (oldKey == newKey) continue
            // Rename the version definition line: oldKey = "..." -> newKey = "..."
            result = result.replace(
                Regex("""(?m)^(\s*)${Regex.escape(oldKey)}(\s*=)"""),
                "$1$newKey$2"
            )
            // Rename all version.ref references
            result = result.replace(
                """version.ref = "$oldKey"""",
                """version.ref = "$newKey""""
            )
        }

        // Rename library aliases in [libraries] section
        for ((oldAlias, newAlias) in aliasRenames) {
            if (oldAlias == newAlias) continue
            // Rename the alias at the start of the line
            result = result.replace(
                Regex("""(?m)^(\s*)${Regex.escape(oldAlias)}(\s*=\s*\{)"""),
                "$1$newAlias$2"
            )
            // Also rename short format: oldAlias = "group:name:version"
            result = result.replace(
                Regex("""(?m)^(\s*)${Regex.escape(oldAlias)}(\s*=\s*")"""),
                "$1$newAlias$2"
            )
        }

        // Rename bundle references (bundle values reference library aliases)
        for ((oldAlias, newAlias) in aliasRenames) {
            if (oldAlias == newAlias) continue
            // In bundles, aliases appear as quoted strings in arrays: "oldAlias" -> "newAlias"
            result = result.replace("\"$oldAlias\"", "\"$newAlias\"")
        }

        return result
    }

    private data class LibraryInfo(
        val alias: String,
        val groupId: String,
        val artifactId: String,
        val versionRef: String?,   // version.ref = "key"
        val directVersion: String? // version = "1.0.0"
    )

    private fun parseLibraryLine(line: String): LibraryInfo? {
        val alias = line.substringBefore("=").trim()
        if (alias.isEmpty() || alias.startsWith("#")) return null

        val groupId: String
        val artifactId: String

        // Try module = "group:artifact" format
        val moduleMatch = Regex("""module\s*=\s*"([^":]+):([^"]+)"""").find(line)
        if (moduleMatch != null) {
            groupId = moduleMatch.groupValues[1]
            artifactId = moduleMatch.groupValues[2]
        } else {
            // Try group = "...", name = "..." format
            val groupMatch = Regex("""group\s*=\s*"([^"]+)"""").find(line)
            val nameMatch = Regex("""\bname\s*=\s*"([^"]+)"""").find(line)
            if (groupMatch != null && nameMatch != null) {
                groupId = groupMatch.groupValues[1]
                artifactId = nameMatch.groupValues[1]
            } else {
                // Try short format: alias = "group:artifact:version"
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

        return LibraryInfo(alias, groupId, artifactId, versionRef, directVersion)
    }

    private fun findLibraryByAlias(lines: List<String>, alias: String): LibraryInfo? {
        var inLibraries = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.matches(Regex("^\\[libraries]\\s*(#.*)?$"))) { inLibraries = true; continue }
            if (trimmed.matches(Regex("^\\[.+]\\s*(#.*)?$"))) { inLibraries = false; continue }
            if (!inLibraries) continue
            val info = parseLibraryLine(trimmed) ?: continue
            if (info.alias == alias) return info
        }
        return null
    }

    private fun toKebabCase(s: String): String {
        return s.replace('.', '-')
            .replace('_', '-')
            .replace(Regex("([a-z])([A-Z])"), "$1-$2")
            .lowercase()
    }
}

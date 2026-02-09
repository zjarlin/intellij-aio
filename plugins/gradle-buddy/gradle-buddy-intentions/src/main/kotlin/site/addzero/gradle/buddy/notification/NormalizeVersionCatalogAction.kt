package site.addzero.gradle.buddy.notification

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import java.io.File

/**
 * Normalize version catalog naming conventions.
 *
 * Rules:
 * 1. Library alias = artifactId in kebab-case
 * 2. If multiple libraries produce the same alias (e.g. different groupId but same artifactId),
 *    use full-qualified `groupId-artifactId` in kebab-case to disambiguate
 * 3. version.ref rename: only when the ref is used by exactly ONE library (shared refs are never touched)
 * 4. [versions] key = same as the library alias (when ref is not shared)
 *
 * After renaming, all `libs.xxx.yyy` accessors in .gradle.kts files are updated accordingly.
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

        val aliasRenames = mutableMapOf<String, String>()
        val newContent = normalizeContent(content, aliasRenames)

        // Collect effective alias renames (filter out no-ops)
        val effectiveAliasRenames = aliasRenames.filter { (old, new) -> old != new }

        WriteCommandAction.runWriteCommandAction(project, "Normalize Version Catalog", null, {
            // Step 1: Update the TOML file
            if (content != newContent) {
                if (document != null) {
                    document.replaceString(0, document.textLength, newContent)
                    PsiDocumentManager.getInstance(project).commitDocument(document)
                    documentManager.saveDocument(document)
                } else {
                    file.setBinaryContent(newContent.toByteArray())
                }
            }

            // Step 2: Update all .gradle.kts references for renamed aliases
            if (effectiveAliasRenames.isNotEmpty()) {
                updateGradleKtsReferences(project, effectiveAliasRenames)
            }
        })
    }

    /**
     * Find all .gradle.kts files in the project and update `libs.xxx.yyy` accessors
     * for every renamed alias.
     *
     * TOML alias `my-lib` maps to kts accessor `libs.my.lib` (hyphens become dots).
     */
    private fun updateGradleKtsReferences(project: Project, aliasRenames: Map<String, String>) {
        val basePath = project.basePath ?: return
        val baseDir = VfsUtil.findFile(File(basePath).toPath(), true) ?: return
        val ktsFiles = mutableListOf<VirtualFile>()
        VfsUtil.processFileRecursivelyWithoutIgnored(baseDir) { vf ->
            if (!vf.isDirectory && vf.name.endsWith(".gradle.kts")) {
                ktsFiles.add(vf)
            }
            true
        }

        val docManager = FileDocumentManager.getInstance()
        val psiDocManager = PsiDocumentManager.getInstance(project)

        // Build accessor rename map: "old.accessor" -> "new.accessor"
        // Sort by longest first to avoid partial replacements
        val accessorRenames = aliasRenames.map { (oldAlias, newAlias) ->
            aliasToAccessor(oldAlias) to aliasToAccessor(newAlias)
        }.sortedByDescending { it.first.length }

        for (ktsFile in ktsFiles) {
            val doc = docManager.getDocument(ktsFile) ?: continue
            var text = doc.text
            var changed = false

            for ((oldAccessor, newAccessor) in accessorRenames) {
                if (oldAccessor == newAccessor) continue

                // Match libs.old.accessor followed by a non-identifier char (or end of string)
                // This handles: libs.old.name, libs.old.name.get(), libs.old.name)
                val pattern = Regex("""libs\.${Regex.escape(oldAccessor)}(?=[^a-zA-Z0-9_]|$)""")
                val replaced = pattern.replace(text, "libs.$newAccessor")
                if (replaced != text) {
                    text = replaced
                    changed = true
                }
            }

            if (changed) {
                doc.replaceString(0, doc.textLength, text)
                psiDocManager.commitDocument(doc)
                docManager.saveDocument(doc)
            }
        }
    }

    /**
     * Convert a TOML alias to its Gradle kts accessor path.
     * e.g. "my-cool-lib" -> "my.cool.lib"
     */
    private fun aliasToAccessor(alias: String): String = alias.replace('-', '.').replace('_', '.')

    /**
     * Normalize the TOML content and populate [aliasRenames] with old->new alias mappings.
     */
    private fun normalizeContent(content: String, aliasRenames: MutableMap<String, String>): String {
        val lines = content.lines()

        // Step 1: Parse all libraries
        val allLibraries = parseAllLibraries(lines)
        if (allLibraries.isEmpty()) return content

        // Step 2: Count how many libraries reference each version.ref variable
        val versionRefUsageCount = mutableMapOf<String, Int>()
        for (lib in allLibraries) {
            val ref = lib.versionRef ?: continue
            versionRefUsageCount[ref] = (versionRefUsageCount[ref] ?: 0) + 1
        }

        // Step 3: Compute desired alias for each library based on artifactId
        // Then detect conflicts (same alias target from different libraries)
        val desiredAliasMap = mutableMapOf<String, String>() // old alias -> desired alias
        for (lib in allLibraries) {
            desiredAliasMap[lib.alias] = toKebabCase(lib.artifactId)
        }

        // Step 4: Detect alias conflicts — multiple libraries mapping to the same desired alias
        // Group by desired alias to find collisions
        val desiredToSources = mutableMapOf<String, MutableList<LibraryInfo>>()
        for (lib in allLibraries) {
            val desired = desiredAliasMap[lib.alias]!!
            desiredToSources.getOrPut(desired) { mutableListOf() }.add(lib)
        }

        // Resolve conflicts: if multiple libraries want the same alias, use full-qualified group-artifact
        for ((desired, libs) in desiredToSources) {
            if (libs.size > 1) {
                // Conflict! Different artifacts (possibly different groupIds) produce the same alias
                for (lib in libs) {
                    val fqAlias = toKebabCase(lib.groupId) + "-" + toKebabCase(lib.artifactId)
                    desiredAliasMap[lib.alias] = fqAlias
                }
            }
        }

        // Populate aliasRenames — only for aliases that actually change
        for ((oldAlias, newAlias) in desiredAliasMap) {
            if (oldAlias != newAlias) {
                aliasRenames[oldAlias] = newAlias
            }
        }

        // Step 5: Determine version key renames — only for version.ref used by exactly 1 library
        val versionKeyRenames = mutableMapOf<String, String>()

        for (lib in allLibraries) {
            val ref = lib.versionRef ?: continue
            val usageCount = versionRefUsageCount[ref] ?: 0
            // If multiple libraries share this version.ref, don't touch it
            if (usageCount > 1) continue

            val newAlias = desiredAliasMap[lib.alias] ?: continue
            if (ref != newAlias) {
                versionKeyRenames[ref] = newAlias
            }
        }

        // Step 6: Handle version key conflicts
        val versionTargetGroups = mutableMapOf<String, MutableList<String>>()
        for ((old, target) in versionKeyRenames) {
            versionTargetGroups.getOrPut(target) { mutableListOf() }.add(old)
        }
        for ((_, sources) in versionTargetGroups) {
            if (sources.size > 1) {
                // Would conflict — don't rename any of them
                for (old in sources) {
                    versionKeyRenames.remove(old)
                }
            }
        }

        // Don't rename a version key if the target name already exists as a different version key
        val existingVersionKeys = parseExistingVersionKeys(lines)
        val toRemove = mutableListOf<String>()
        for ((oldKey, newKey) in versionKeyRenames) {
            if (newKey in existingVersionKeys && oldKey != newKey && existingVersionKeys[newKey] != oldKey) {
                toRemove.add(oldKey)
            }
        }
        toRemove.forEach { versionKeyRenames.remove(it) }

        // Check alias conflicts with existing aliases that aren't being renamed
        val existingAliases = allLibraries.map { it.alias }.toSet()
        val aliasToRemove = mutableListOf<String>()
        for ((oldAlias, newAlias) in aliasRenames) {
            if (newAlias in existingAliases && newAlias != oldAlias && !aliasRenames.containsKey(newAlias)) {
                // Target alias already exists and isn't itself being renamed away — skip
                aliasToRemove.add(oldAlias)
            }
        }
        aliasToRemove.forEach { aliasRenames.remove(it) }

        // Step 7: Apply renames
        // IMPORTANT: Sort renames by old alias length descending to avoid prefix collisions
        // e.g. "lazy-people-http" must not match "lazy-people-http-lib"
        var result = content

        // Rename version keys in [versions] section and all version.ref references
        for ((oldKey, newKey) in versionKeyRenames) {
            if (oldKey == newKey) continue
            // Match version key at line start, followed by whitespace and =
            // Use word boundary after the key to avoid prefix matching
            result = result.replace(
                Regex("""(?m)^(\s*)${Regex.escape(oldKey)}(?=\s*=)"""),
                "$1$newKey"
            )
            result = result.replace(
                """version.ref = "$oldKey"""",
                """version.ref = "$newKey""""
            )
        }

        // Rename library aliases — sort by longest first to prevent prefix collisions
        val sortedAliasRenames = aliasRenames.entries
            .filter { it.key != it.value }
            .sortedByDescending { it.key.length }

        // To avoid chain renames (A→B then B→C), do all replacements in one pass per line
        // Strategy: replace line by line in [libraries] section
        val resultLines = result.lines().toMutableList()
        var inLibrariesSection = false
        var inBundlesSection = false
        for (i in resultLines.indices) {
            val trimmed = resultLines[i].trim()
            if (trimmed.matches(SECTION_HEADER_REGEX)) {
                inLibrariesSection = trimmed.matches(LIBRARIES_HEADER_REGEX)
                inBundlesSection = trimmed.matches(BUNDLES_HEADER_REGEX)
                continue
            }

            if (inLibrariesSection && trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                // Extract the alias at the start of the line (before first =)
                val lineAlias = trimmed.substringBefore("=").trim()
                val rename = aliasRenames[lineAlias]
                if (rename != null && rename != lineAlias) {
                    // Replace only the exact alias at the beginning of the line
                    resultLines[i] = resultLines[i].replaceFirst(lineAlias, rename)
                }
            }

            if (inBundlesSection && trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                // In bundles, aliases appear as quoted strings in arrays: ["alias1", "alias2"]
                var line = resultLines[i]
                for ((oldAlias, newAlias) in sortedAliasRenames) {
                    // Match exact alias in quotes within bundle arrays
                    line = line.replace(
                        Regex(""""${Regex.escape(oldAlias)}""""),
                        "\"$newAlias\""
                    )
                }
                resultLines[i] = line
            }
        }

        result = resultLines.joinToString("\n")

        return result
    }

    private fun parseAllLibraries(lines: List<String>): List<LibraryInfo> {
        val result = mutableListOf<LibraryInfo>()
        var inLibraries = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.matches(SECTION_HEADER_REGEX)) {
                inLibraries = trimmed.matches(LIBRARIES_HEADER_REGEX)
                continue
            }
            if (!inLibraries || trimmed.isBlank() || trimmed.startsWith("#")) continue
            parseLibraryLine(trimmed)?.let { result.add(it) }
        }
        return result
    }

    private fun parseExistingVersionKeys(lines: List<String>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        var inVersions = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.matches(SECTION_HEADER_REGEX)) {
                inVersions = trimmed.matches(VERSIONS_HEADER_REGEX)
                continue
            }
            if (!inVersions || trimmed.isBlank() || trimmed.startsWith("#")) continue
            val key = trimmed.substringBefore("=").trim()
            if (key.isNotEmpty()) map[key] = key
        }
        return map
    }

    private data class LibraryInfo(
        val alias: String,
        val groupId: String,
        val artifactId: String,
        val versionRef: String?,
        val directVersion: String?
    )

    private fun parseLibraryLine(line: String): LibraryInfo? {
        val alias = line.substringBefore("=").trim()
        if (alias.isEmpty() || alias.startsWith("#")) return null

        val groupId: String
        val artifactId: String

        val moduleMatch = MODULE_PATTERN.find(line)
        if (moduleMatch != null) {
            groupId = moduleMatch.groupValues[1]
            artifactId = moduleMatch.groupValues[2]
        } else {
            val groupMatch = GROUP_PATTERN.find(line)
            val nameMatch = NAME_PATTERN.find(line)
            if (groupMatch != null && nameMatch != null) {
                groupId = groupMatch.groupValues[1]
                artifactId = nameMatch.groupValues[1]
            } else {
                val shortMatch = SHORT_FORMAT_PATTERN.find(line)
                if (shortMatch != null) {
                    groupId = shortMatch.groupValues[1]
                    artifactId = shortMatch.groupValues[2]
                } else {
                    return null
                }
            }
        }

        val versionRef = VERSION_REF_PATTERN.find(line)?.groupValues?.get(1)
        val directVersion = DIRECT_VERSION_PATTERN.find(line)?.groupValues?.get(1)

        return LibraryInfo(alias, groupId, artifactId, versionRef, directVersion)
    }

    private fun toKebabCase(s: String): String {
        return s.replace('.', '-')
            .replace('_', '-')
            .replace(Regex("([a-z])([A-Z])"), "$1-$2")
            .lowercase()
    }

    companion object {
        private val SECTION_HEADER_REGEX = Regex("^\\[.+]\\s*(#.*)?$")
        private val LIBRARIES_HEADER_REGEX = Regex("^\\[libraries]\\s*(#.*)?$")
        private val VERSIONS_HEADER_REGEX = Regex("^\\[versions]\\s*(#.*)?$")
        private val BUNDLES_HEADER_REGEX = Regex("^\\[bundles]\\s*(#.*)?$")
        private val MODULE_PATTERN = Regex("""module\s*=\s*"([^":]+):([^"]+)"""")
        private val GROUP_PATTERN = Regex("""group\s*=\s*"([^"]+)"""")
        private val NAME_PATTERN = Regex("""\bname\s*=\s*"([^"]+)"""")
        private val SHORT_FORMAT_PATTERN = Regex("""=\s*"([^":]+):([^":]+):([^"]+)"""")
        private val VERSION_REF_PATTERN = Regex("""version\.ref\s*=\s*"([^"]+)"""")
        private val DIRECT_VERSION_PATTERN = Regex("""(?<!\.)\bversion\s*=\s*"([^"]+)"""")
    }
}

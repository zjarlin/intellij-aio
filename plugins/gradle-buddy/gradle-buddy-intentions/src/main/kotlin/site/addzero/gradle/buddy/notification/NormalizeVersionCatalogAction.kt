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
import site.addzero.gradle.buddy.settings.GradleBuddySettingsService
import java.io.File

/**
 * Normalize version catalog naming conventions.
 *
 * Rules:
 * 1. Library alias = groupId-artifactId in kebab-case (preserves semantic context)
 * 2. If multiple libraries produce the same alias (same group:artifact, different versions),
 *    append -vVersion to disambiguate
 * 3. version.ref rename: only when the ref is used by exactly ONE library (shared refs are never touched)
 * 4. [versions] key = same as the library alias (when ref is not shared)
 * 5. Accessor name clashes (e.g. navigation-compose vs navigationCompose) are merged,
 *    keeping the higher version
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
        val newContent = normalizeContent(content, aliasRenames, project)

        // Collect effective alias renames (filter out no-ops)
        val effectiveAliasRenames = aliasRenames.filter { (old, new) -> old != new }

        // 二次确认弹窗：Normalize 会修改整个项目的 .gradle.kts 引用，属于危险操作
        val tomlChanged = content != newContent
        val ktsAffected = effectiveAliasRenames.isNotEmpty()

        if (!tomlChanged && !ktsAffected) {
            com.intellij.openapi.ui.Messages.showInfoMessage(
                project,
                "Version catalog is already normalized. No changes needed.",
                "Normalize Version Catalog"
            )
            return
        }

        val warningMessage = buildString {
            append("⚠️ Normalize will perform the following changes:\n\n")
            if (tomlChanged) {
                append("• Rename aliases and version keys in ${file.name}\n")
            }
            if (ktsAffected) {
                append("• Update libs.xxx.yyy references in ALL .gradle.kts files across the project\n")
                append("• ${effectiveAliasRenames.size} alias(es) will be renamed\n")
                if (effectiveAliasRenames.size <= 10) {
                    append("\nRenames:\n")
                    for ((old, new) in effectiveAliasRenames) {
                        append("  $old → $new\n")
                    }
                } else {
                    append("\nFirst 10 renames:\n")
                    for ((old, new) in effectiveAliasRenames.entries.take(10)) {
                        append("  $old → $new\n")
                    }
                    append("  ... and ${effectiveAliasRenames.size - 10} more\n")
                }
            }
            append("\n⚠️ This is a project-wide destructive operation. Please commit your code before proceeding.")
            append("\n\nContinue?")
        }

        val result = com.intellij.openapi.ui.Messages.showYesNoDialog(
            project,
            warningMessage,
            "Normalize Version Catalog — Confirm",
            "Normalize",
            "Cancel",
            com.intellij.openapi.ui.Messages.getWarningIcon()
        )
        if (result != com.intellij.openapi.ui.Messages.YES) return

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

            // Step 3: Second-pass verification — find and fix any remaining broken references
            // Re-read the (possibly updated) TOML to get the ground-truth alias set,
            // then scan all .gradle.kts files for libs.* accessors that don't match any alias.
            val updatedToml = if (document != null) document.text else String(file.contentsToByteArray())
            verifyAndFixBrokenReferences(project, updatedToml, file)
        })
    }

    /**
     * Second-pass verification: scan all .gradle.kts files for `libs.*` references
     * that don't correspond to any alias in the TOML, and fix them by matching
     * via multiple strategies:
     *
     * 1. Exact normalized accessor match (handles camelCase vs kebab-case)
     * 2. Token suffix match — if the broken ref's tokens are a suffix of exactly one
     *    TOML alias's tokens, that's the match. This handles the common case where
     *    a short alias like `ksp-gradle-plugin` was renamed to the full
     *    `com-google-devtools-ksp-com-google-devtools-ksp-gradle-plugin`.
     * 3. Token subset + order match — if all tokens of the broken ref appear in exactly
     *    one TOML alias in the same order, that's the match.
     *
     * Only unambiguous (single-candidate) matches are applied automatically.
     */
    private fun verifyAndFixBrokenReferences(project: Project, tomlContent: String, tomlFile: VirtualFile) {
        // Parse all valid aliases from the current TOML (libraries + plugins + versions + bundles)
        val tomlLines = tomlContent.lines()
        val libraryAliases = parseAllLibraries(tomlLines).map { it.alias }
        val pluginAliases = parsePluginAliases(tomlLines)
        val versionKeys = parseVersionValues(tomlLines).keys.toList()
        val bundleAliases = parseBundleAliases(tomlLines)

        // Build accessor -> toml-alias map for libraries
        val accessorToAlias = mutableMapOf<String, String>()
        for (alias in libraryAliases) {
            val accessor = aliasToAccessor(alias)
            accessorToAlias[accessor] = alias
        }
        // Plugins: in kts they appear as libs.plugins.xxx.yyy
        for (alias in pluginAliases) {
            val accessor = "plugins." + aliasToAccessor(alias)
            accessorToAlias[accessor] = alias
        }
        // Versions: in kts they appear as libs.versions.xxx.yyy
        for (key in versionKeys) {
            val accessor = "versions." + aliasToAccessor(key)
            accessorToAlias[accessor] = key
        }
        // Bundles: in kts they appear as libs.bundles.xxx.yyy
        for (alias in bundleAliases) {
            val accessor = "bundles." + aliasToAccessor(alias)
            accessorToAlias[accessor] = alias
        }

        // Build normalized-accessor -> accessor map for exact normalized matching
        val normalizedToAccessors = mutableMapOf<String, MutableList<String>>()
        for (accessor in accessorToAlias.keys) {
            val normalized = toGradleAccessorName(accessor)
            normalizedToAccessors.getOrPut(normalized) { mutableListOf() }.add(accessor)
        }

        // Pre-tokenize all valid accessors for suffix/subset matching
        val accessorTokensMap = accessorToAlias.keys.associateWith { acc ->
            acc.split('.').map { it.lowercase() }
        }

        // Determine the catalog name from the TOML file name
        val catalogName = tomlFile.nameWithoutExtension.removeSuffix(".versions")

        // Scan all .gradle.kts files
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

        val catalogRefPattern = Regex("""(?<!\w)${Regex.escape(catalogName)}\.([a-zA-Z0-9_.]+)""")

        for (ktsFile in ktsFiles) {
            val doc = docManager.getDocument(ktsFile) ?: continue
            var text = doc.text
            var changed = false

            val matches = catalogRefPattern.findAll(text).toList().sortedByDescending { it.range.first }
            for (match in matches) {
                val rawAccessor = match.groupValues[1]
                // Skip false positives (reflection chains, dynamic API calls, catalog declarations)
                if (shouldSkipReference(rawAccessor, text, match, ktsFile)) continue
                // Strip trailing Gradle Provider API method calls captured by the regex
                val refAccessor = stripTrailingMethods(rawAccessor)
                // Already valid — skip
                if (refAccessor in accessorToAlias) continue

                // Strategy 0: Duplicate catalog prefix — e.g. libs.libs.xxx → strip leading "libs."
                val dupPrefix = catalogName + "."
                val effectiveRef = if (refAccessor.startsWith(dupPrefix)) {
                    stripTrailingMethods(refAccessor.removePrefix(dupPrefix))
                } else refAccessor

                val correctAccessor = if (effectiveRef != refAccessor && effectiveRef in accessorToAlias) {
                    effectiveRef  // exact match after stripping duplicate prefix
                } else {
                    findBestMatch(effectiveRef, normalizedToAccessors, accessorTokensMap)
                }

                if (correctAccessor != null && correctAccessor != refAccessor) {
                    val fullOld = "$catalogName.$refAccessor"
                    val fullNew = "$catalogName.$correctAccessor"
                    text = text.substring(0, match.range.first) + fullNew + text.substring(match.range.last + 1)
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
     * Determine whether a catalog reference match should be skipped (false positive).
     *
     * Filters out:
     * 1. Java/Kotlin reflection chains: `libs.javaClass.superclass.protectionDomain...`
     * 2. Dynamic catalog API calls: `libs.findLibrary(...)`, `libs.findBundle(...)`, etc.
     * 3. Catalog declaration blocks in settings: `create("libs") { from(...) }` inside `versionCatalogs`
     * 4. String literal context: match inside a quoted string
     */
    private fun shouldSkipReference(
        refAccessor: String,
        fileText: String,
        match: MatchResult,
        file: VirtualFile
    ): Boolean {
        val firstSegment = refAccessor.substringBefore('.')
        if (firstSegment in REFLECTION_TOKENS) return true
        if (firstSegment in DYNAMIC_API_METHODS) return true

        if (file.name == "settings.gradle.kts") {
            val matchStart = match.range.first
            val textBefore = fileText.substring(0, matchStart)
            val catalogBlockStart = textBefore.lastIndexOf("versionCatalogs")
            if (catalogBlockStart >= 0) {
                val between = textBefore.substring(catalogBlockStart)
                val openBraces = between.count { it == '{' }
                val closeBraces = between.count { it == '}' }
                if (openBraces > closeBraces) return true
            }
        }

        val matchStart = match.range.first
        val lineStart = fileText.lastIndexOf('\n', matchStart - 1) + 1
        val lineEnd = fileText.indexOf('\n', matchStart).let { if (it < 0) fileText.length else it }
        val line = fileText.substring(lineStart, lineEnd)
        val posInLine = matchStart - lineStart
        val quotesBeforeMatch = line.substring(0, posInLine).count { it == '"' }
        if (quotesBeforeMatch % 2 == 1) return true

        return false
    }

    /**
     * Find the best matching accessor for a broken reference using multiple strategies.
     * Returns null if no unambiguous match is found.
     */
    private fun findBestMatch(
        brokenRef: String,
        normalizedToAccessors: Map<String, MutableList<String>>,
        accessorTokensMap: Map<String, List<String>>
    ): String? {
        // Strategy 1: Exact normalized accessor match
        val normalizedRef = toGradleAccessorName(brokenRef)
        val exactCandidates = normalizedToAccessors[normalizedRef]
        if (exactCandidates != null && exactCandidates.size == 1) {
            return exactCandidates[0]
        }

        // Strategy 2: Token suffix match
        // e.g. broken "ksp.gradle.plugin" tokens = [ksp, gradle, plugin]
        //      valid  "com.google.devtools.ksp.com.google.devtools.ksp.gradle.plugin" ends with [ksp, gradle, plugin]
        val refTokens = brokenRef.split('.').map { it.lowercase() }
        if (refTokens.isEmpty()) return null

        val suffixCandidates = accessorTokensMap.entries.filter { (_, tokens) ->
            tokens.size > refTokens.size && tokens.takeLast(refTokens.size) == refTokens
        }.map { it.key }

        if (suffixCandidates.size == 1) {
            return suffixCandidates[0]
        }

        // Strategy 3: Token ordered-subset match
        // All tokens of the broken ref appear in the candidate in the same relative order
        val subsetCandidates = accessorTokensMap.entries.filter { (_, tokens) ->
            tokens.size > refTokens.size && isOrderedSubset(refTokens, tokens)
        }.map { it.key }

        if (subsetCandidates.size == 1) {
            return subsetCandidates[0]
        }

        // Ambiguous or no match
        return null
    }

    /**
     * Check if [sub] is an ordered subset of [full].
     * Every element of [sub] must appear in [full] in the same relative order.
     */
    private fun isOrderedSubset(sub: List<String>, full: List<String>): Boolean {
        var fi = 0
        for (token in sub) {
            val idx = full.subList(fi, full.size).indexOf(token)
            if (idx < 0) return false
            fi += idx + 1
        }
        return true
    }

    /**
     * Parse plugin aliases from [plugins] section.
     */
    private fun parsePluginAliases(lines: List<String>): List<String> {
        val result = mutableListOf<String>()
        var inPlugins = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.matches(SECTION_HEADER_REGEX)) {
                inPlugins = trimmed.matches(PLUGINS_HEADER_REGEX)
                continue
            }
            if (!inPlugins || trimmed.isBlank() || trimmed.startsWith("#")) continue
            val alias = trimmed.substringBefore("=").trim()
            if (alias.isNotEmpty()) result.add(alias)
        }
        return result
    }

    /**
     * Parse bundle aliases from [bundles] section.
     */
    private fun parseBundleAliases(lines: List<String>): List<String> {
        val result = mutableListOf<String>()
        var inBundles = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.matches(SECTION_HEADER_REGEX)) {
                inBundles = trimmed.matches(BUNDLES_HEADER_REGEX)
                continue
            }
            if (!inBundles || trimmed.isBlank() || trimmed.startsWith("#")) continue
            val alias = trimmed.substringBefore("=").trim()
            if (alias.isNotEmpty()) result.add(alias)
        }
        return result
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
     * Strip trailing Gradle Provider API method names that get captured by the regex.
     * e.g. "versions.jdk.get" → "versions.jdk"
     */
    private fun stripTrailingMethods(accessor: String): String {
        var result = accessor
        while (true) {
            val lastDot = result.lastIndexOf('.')
            if (lastDot < 0) break
            val lastSegment = result.substring(lastDot + 1)
            if (lastSegment in PROVIDER_API_METHODS) {
                result = result.substring(0, lastDot)
            } else {
                break
            }
        }
        return result
    }

    /**
     * Normalize the TOML content and populate [aliasRenames] with old->new alias mappings.
     */
    private fun normalizeContent(content: String, aliasRenames: MutableMap<String, String>, project: Project): String {
        // Step 0: Merge Gradle-equivalent version keys that clash on accessor name
        // e.g. navigation-compose = "2.9.7" and navigationCompose = "2.9.1"
        // both map to getNavigationComposeVersion() — keep the higher version
        val mergedContent = mergeClashingVersionKeys(content)

        val lines = mergedContent.lines()

        // Step 1: Parse all libraries
        val allLibraries = parseAllLibraries(lines)
        if (allLibraries.isEmpty()) return mergedContent

        // Parse [versions] section to resolve version.ref → actual version value
        val versionValues = parseVersionValues(lines)

        // Step 2: Count how many libraries reference each version.ref variable
        val versionRefUsageCount = mutableMapOf<String, Int>()
        for (lib in allLibraries) {
            val ref = lib.versionRef ?: continue
            versionRefUsageCount[ref] = (versionRefUsageCount[ref] ?: 0) + 1
        }

        // Step 3-4: Three-level alias deduplication
        // Level 1: artifactId
        // Level 2: groupId-artifactId (if artifactId collides)
        // Level 3: groupId-artifactId-vVersion (if group:artifact also collides due to different versions)
        val desiredAliasMap = computeDesiredAliases(allLibraries, versionValues, project)

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
        var result = mergedContent

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

    /**
     * Two-level alias deduplication:
     * 1. Default: groupId-artifactId in kebab-case
     * 2. If still clashing (same group:artifact, different versions): disambiguate based on user setting
     *    - MAJOR_VERSION (default): extract first number from version → -v2, -v3
     *    - ALT_SUFFIX: append -alt, -alt2, -alt3
     *
     * Clash detection uses Gradle accessor normalization (not just string equality) to catch
     * cases like "foo-bar" vs "fooBar" mapping to the same accessor.
     */
    private fun computeDesiredAliases(
        allLibraries: List<LibraryInfo>,
        versionValues: Map<String, String>,
        project: Project
    ): Map<String, String> {
        val desiredAliasMap = mutableMapOf<String, String>()
        val strategy = GradleBuddySettingsService.getInstance(project).getNormalizeDedupStrategy()

        // Level 1: groupId-artifactId
        for (lib in allLibraries) {
            desiredAliasMap[lib.alias] = toKebabCase(lib.groupId) + "-" + toKebabCase(lib.artifactId)
        }

        // Level 1 conflict detection via Gradle accessor name
        val level1Groups = allLibraries.groupBy { toGradleAccessorName(desiredAliasMap[it.alias]!!) }
        for ((_, libs) in level1Groups) {
            if (libs.size <= 1) continue

            // Sort by version descending so the highest version gets the "best" suffix
            val sorted = libs.sortedByDescending { resolveVersion(it, versionValues) ?: "" }

            when (strategy) {
                "MAJOR_VERSION" -> {
                    // Extract major version number from each lib's version
                    // e.g. 2.7.18 → "2", 3.2.0 → "3"
                    // If major versions differ → -v2, -v3 (unique, no further dedup needed)
                    // If major versions collide → -v2, -v2alt2 (append alt counter)
                    val majorVersionMap = mutableMapOf<String, MutableList<LibraryInfo>>()
                    for (lib in sorted) {
                        val version = resolveVersion(lib, versionValues) ?: ""
                        val major = extractMajorVersion(version)
                        majorVersionMap.getOrPut(major) { mutableListOf() }.add(lib)
                    }
                    for ((major, groupLibs) in majorVersionMap) {
                        if (groupLibs.size == 1) {
                            val lib = groupLibs[0]
                            val base = desiredAliasMap[lib.alias]!!
                            desiredAliasMap[lib.alias] = "$base-v$major"
                        } else {
                            // Same major version — append -v{major}, -v{major}alt2, -v{major}alt3, ...
                            for ((idx, lib) in groupLibs.withIndex()) {
                                val base = desiredAliasMap[lib.alias]!!
                                val suffix = if (idx == 0) "-v$major" else "-v${major}alt${idx + 1}"
                                desiredAliasMap[lib.alias] = base + suffix
                            }
                        }
                    }
                }
                else -> {
                    // ALT_SUFFIX strategy: -alt, -alt2, -alt3, ...
                    for ((idx, lib) in sorted.withIndex()) {
                        val base = desiredAliasMap[lib.alias]!!
                        val suffix = if (idx == 0) "-alt" else "-alt${idx + 1}"
                        desiredAliasMap[lib.alias] = base + suffix
                    }
                }
            }
        }

        // Level 2 conflict check via accessor name — if still colliding, keep original alias
        val level2Groups = allLibraries.groupBy { toGradleAccessorName(desiredAliasMap[it.alias]!!) }
        for ((_, libs) in level2Groups) {
            if (libs.size <= 1) continue
            for (lib in libs) {
                desiredAliasMap[lib.alias] = lib.alias
            }
        }

        return desiredAliasMap
    }

    /**
     * Extract the major version number (first numeric segment) from a version string.
     * e.g. "2.7.18" → "2", "4.1.0-M1" → "4", "v3.0" → "3", "" → "0"
     */
    private fun extractMajorVersion(version: String): String {
        val match = Regex("""(\d+)""").find(version)
        return match?.groupValues?.get(1) ?: "0"
    }

    /**
     * Resolve the actual version string for a library.
     * Checks version.ref → [versions] lookup, then directVersion.
     */
    private fun resolveVersion(lib: LibraryInfo, versionValues: Map<String, String>): String? {
        if (lib.versionRef != null) {
            return versionValues[lib.versionRef] ?: lib.versionRef
        }
        return lib.directVersion
    }

    /**
     * Parse [versions] section to get key → value mapping.
     * e.g. springBoot = "3.2.0" → {"springBoot": "3.2.0"}
     */
    private fun parseVersionValues(lines: List<String>): Map<String, String> {
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
            // Value can be: "3.2.0" or { strictly = "...", prefer = "..." }
            val valueMatch = Regex("""=\s*"([^"]+)"""").find(trimmed)
            if (key.isNotEmpty() && valueMatch != null) {
                map[key] = valueMatch.groupValues[1]
            }
        }
        return map
    }

    /**
     * Sanitize a version string for use in a TOML alias.
     * TOML alias allows: [a-zA-Z0-9], '-', '_'
     * e.g. "4.1.0-M1" → "4-1-0-m1"
     *      "3.2.0" → "3-2-0"
     */
    private fun sanitizeVersionForAlias(version: String): String {
        return version
            .replace('.', '-')
            .replace(Regex("[^a-zA-Z0-9\\-]"), "-") // replace any non-alphanumeric/non-hyphen
            .replace(Regex("-{2,}"), "-")             // collapse consecutive hyphens
            .trim('-')
            .lowercase()
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

    /**
     * Compute the Gradle accessor name for a TOML key.
     *
     * Gradle normalizes catalog keys by treating `-`, `_`, `.` and camelCase boundaries
     * as equivalent separators. All of these map to the same accessor method.
     * e.g. "navigation-compose", "navigationCompose", "navigation_compose", "navigation.compose"
     * all produce `getNavigationCompose...()`.
     *
     * Algorithm: split on `-`, `_`, `.` and camelCase boundaries, then join lowercase.
     */
    private fun toGradleAccessorName(key: String): String {
        return key
            // Insert separator before uppercase letters in camelCase: "navigationCompose" → "navigation-Compose"
            .replace(Regex("([a-z0-9])([A-Z])"), "$1-$2")
            // Split on any separator
            .split(Regex("[\\-_.]"))
            .joinToString("") { it.lowercase() }
    }

    /**
     * Merge version keys in [versions] that clash on Gradle accessor name.
     *
     * For example, `navigation-compose = "2.9.7"` and `navigationCompose = "2.9.1"`
     * both generate `getNavigationComposeVersion()` — Gradle rejects this.
     *
     * Strategy:
     * - Group version keys by their normalized accessor name
     * - For clashing groups, keep the entry with the higher version (semver comparison)
     * - Use kebab-case as the canonical key form
     * - Rewrite all version.ref references in [libraries] and [plugins] to point to the surviving key
     *
     * Also applies the same logic to [libraries] and [plugins] keys.
     */
    private fun mergeClashingVersionKeys(content: String): String {
        val lines = content.lines().toMutableList()

        // --- Phase 1: Merge clashing [versions] entries ---
        val versionEntries = parseSectionEntries(lines, VERSIONS_HEADER_REGEX)
        val versionClashGroups = versionEntries
            .groupBy { toGradleAccessorName(it.key) }
            .filter { it.value.size > 1 }

        if (versionClashGroups.isEmpty()) return content

        // For each clash group: pick the winner (highest version), record losers
        // loserKey → winnerKey
        val versionKeyRedirects = mutableMapOf<String, String>()
        val linesToRemove = mutableSetOf<Int>()

        for ((_, clashEntries) in versionClashGroups) {
            // Sort by parsed version descending; on tie, prefer kebab-case form
            val sorted = clashEntries.sortedWith(
                compareByDescending<SectionEntry> { parseComparableVersion(it.value) }
                    .thenBy { it.key.contains(Regex("[A-Z]")).not() } // prefer kebab-case
            )
            val winner = sorted.first()
            // Normalize winner key to kebab-case
            val canonicalKey = toKebabCase(winner.key)

            for (entry in sorted) {
                if (entry !== winner) {
                    versionKeyRedirects[entry.key] = canonicalKey
                    linesToRemove.add(entry.lineIndex)
                }
            }
            // If winner key itself isn't canonical, rename it
            if (winner.key != canonicalKey) {
                versionKeyRedirects[winner.key] = canonicalKey
            }
        }

        // Remove loser lines (iterate in reverse to preserve indices)
        for (idx in linesToRemove.sortedDescending()) {
            lines.removeAt(idx)
        }

        // Rename winner keys that aren't canonical yet
        var inVersions = false
        for (i in lines.indices) {
            val trimmed = lines[i].trim()
            if (trimmed.matches(SECTION_HEADER_REGEX)) {
                inVersions = trimmed.matches(VERSIONS_HEADER_REGEX)
                continue
            }
            if (!inVersions || trimmed.isBlank() || trimmed.startsWith("#")) continue
            val lineKey = trimmed.substringBefore("=").trim()
            val redirect = versionKeyRedirects[lineKey]
            if (redirect != null && redirect != lineKey) {
                lines[i] = lines[i].replaceFirst(lineKey, redirect)
            }
        }

        // Rewrite version.ref references throughout the file
        var result = lines.joinToString("\n")
        for ((oldKey, newKey) in versionKeyRedirects) {
            if (oldKey == newKey) continue
            result = result.replace(
                """version.ref = "$oldKey"""",
                """version.ref = "$newKey""""
            )
        }

        // --- Phase 2: Merge clashing [libraries] keys ---
        result = mergeSectionClashes(result, LIBRARIES_HEADER_REGEX)

        // --- Phase 3: Merge clashing [plugins] keys ---
        result = mergeSectionClashes(result, PLUGINS_HEADER_REGEX)

        return result
    }

    /**
     * For a given section, detect keys that clash on Gradle accessor name and
     * remove duplicates (keep the first occurrence).
     */
    private fun mergeSectionClashes(content: String, sectionRegex: Regex): String {
        val lines = content.lines().toMutableList()
        val entries = parseSectionEntries(lines, sectionRegex)
        val clashGroups = entries
            .groupBy { toGradleAccessorName(it.key) }
            .filter { it.value.size > 1 }

        if (clashGroups.isEmpty()) return content

        val linesToRemove = mutableSetOf<Int>()
        for ((_, clashEntries) in clashGroups) {
            // Keep first, remove rest
            for (entry in clashEntries.drop(1)) {
                linesToRemove.add(entry.lineIndex)
            }
        }

        for (idx in linesToRemove.sortedDescending()) {
            lines.removeAt(idx)
        }
        return lines.joinToString("\n")
    }

    private data class SectionEntry(val key: String, val value: String, val lineIndex: Int)

    /**
     * Parse key-value entries from a specific TOML section.
     */
    private fun parseSectionEntries(lines: List<String>, sectionRegex: Regex): List<SectionEntry> {
        val entries = mutableListOf<SectionEntry>()
        var inSection = false
        for ((i, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.matches(SECTION_HEADER_REGEX)) {
                inSection = trimmed.matches(sectionRegex)
                continue
            }
            if (!inSection || trimmed.isBlank() || trimmed.startsWith("#")) continue
            val key = trimmed.substringBefore("=").trim()
            val rawValue = trimmed.substringAfter("=").trim()
            val value = rawValue.removeSurrounding("\"")
            if (key.isNotEmpty()) {
                entries.add(SectionEntry(key, value, i))
            }
        }
        return entries
    }

    /**
     * Parse a version string into a comparable list for semver-like comparison.
     * Handles: "2.9.7", "1.0.0-alpha", "2.9.1", etc.
     * Returns a comparable string where numeric segments are zero-padded for proper ordering.
     */
    private fun parseComparableVersion(version: String): String {
        return version.split(Regex("[.\\-_]")).joinToString(".") { segment ->
            val num = segment.toIntOrNull()
            if (num != null) "%010d".format(num) else segment.lowercase()
        }
    }

    companion object {
        private val SECTION_HEADER_REGEX = Regex("^\\[.+]\\s*(#.*)?$")
        private val LIBRARIES_HEADER_REGEX = Regex("^\\[libraries]\\s*(#.*)?$")
        private val VERSIONS_HEADER_REGEX = Regex("^\\[versions]\\s*(#.*)?$")
        private val BUNDLES_HEADER_REGEX = Regex("^\\[bundles]\\s*(#.*)?$")
        private val PLUGINS_HEADER_REGEX = Regex("^\\[plugins]\\s*(#.*)?$")
        private val MODULE_PATTERN = Regex("""module\s*=\s*"([^":]+):([^"]+)"""")
        private val GROUP_PATTERN = Regex("""group\s*=\s*"([^"]+)"""")
        private val NAME_PATTERN = Regex("""\bname\s*=\s*"([^"]+)"""")
        private val SHORT_FORMAT_PATTERN = Regex("""=\s*"([^":]+):([^":]+):([^"]+)"""")
        private val VERSION_REF_PATTERN = Regex("""version\.ref\s*=\s*"([^"]+)"""")
        private val DIRECT_VERSION_PATTERN = Regex("""(?<!\.)\bversion\s*=\s*"([^"]+)"""")

        /** JVM reflection / meta tokens that are never catalog accessors */
        private val REFLECTION_TOKENS = setOf(
            "javaClass", "class", "superclass", "protectionDomain",
            "codeSource", "classLoader", "kotlin"
        )

        /** Dynamic catalog API methods — `libs.findLibrary(...)`, etc. */
        private val DYNAMIC_API_METHODS = setOf(
            "findLibrary", "findBundle", "findPlugin", "findVersion"
        )

        /** Gradle Provider API methods that get captured as trailing accessor segments */
        private val PROVIDER_API_METHODS = setOf(
            "get", "getOrNull", "orNull", "asProvider", "map", "flatMap",
            "orElse", "forUseAtConfigurationTime", "toString"
        )
    }
}

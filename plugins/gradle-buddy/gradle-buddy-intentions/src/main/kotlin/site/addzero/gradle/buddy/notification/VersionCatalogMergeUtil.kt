package site.addzero.gradle.buddy.notification

/**
 * 合并另一个 Version Catalog TOML，并在冲突时尽量保留更高版本。
 */
object VersionCatalogMergeUtil {

    fun merge(existingContent: String, importedContent: String): MergeResult {
        val existing = parseCatalog(existingContent)
        val imported = parseCatalog(importedContent)

        val mergedVersions = linkedMapOf<String, String>()
        mergeVersionMap(mergedVersions, existing.versions)
        mergeVersionMap(mergedVersions, imported.versions)

        val mergedLibraries = linkedMapOf<String, CatalogLibraryEntry>()
        existing.libraries.values.forEach { entry ->
            mergedLibraries[entry.alias] = entry.copy(inlineFields = LinkedHashMap(entry.inlineFields))
        }
        val libraryAliasRedirects = mutableMapOf<String, String>()
        val libraryAliases = mergedLibraries.keys.toMutableSet()
        val librariesByCoordinate = mergedLibraries.values
            .mapNotNull { entry -> entry.coordinateKey()?.let { it to entry.alias } }
            .toMap()
            .toMutableMap()

        imported.libraries.values.forEach { importedEntry ->
            val working = importedEntry.copy(inlineFields = LinkedHashMap(importedEntry.inlineFields))
            val coordinateKey = working.coordinateKey()
            val existingAlias = coordinateKey?.let(librariesByCoordinate::get)
            if (existingAlias != null) {
                val mergedEntry = mergeLibraryEntry(
                    existing = mergedLibraries.getValue(existingAlias),
                    incoming = working,
                    versionMap = mergedVersions
                )
                mergedLibraries[existingAlias] = mergedEntry
                libraryAliasRedirects[importedEntry.alias] = existingAlias
                return@forEach
            }

            val finalAlias = if (libraryAliases.add(working.alias)) {
                working.alias
            } else {
                generateUniqueAlias(
                    preferredAlias = working.preferredAlias(),
                    occupiedAliases = libraryAliases
                )
            }
            working.alias = finalAlias
            mergedLibraries[finalAlias] = working
            libraryAliasRedirects[importedEntry.alias] = finalAlias
            working.coordinateKey()?.let { librariesByCoordinate[it] = finalAlias }
        }

        val mergedPlugins = linkedMapOf<String, CatalogPluginEntry>()
        existing.plugins.values.forEach { entry ->
            mergedPlugins[entry.alias] = entry.copy(inlineFields = LinkedHashMap(entry.inlineFields))
        }
        val pluginAliases = mergedPlugins.keys.toMutableSet()
        val pluginsById = mergedPlugins.values
            .mapNotNull { entry -> entry.pluginId?.takeIf { it.isNotBlank() }?.let { it to entry.alias } }
            .toMap()
            .toMutableMap()

        imported.plugins.values.forEach { importedEntry ->
            val working = importedEntry.copy(inlineFields = LinkedHashMap(importedEntry.inlineFields))
            val pluginId = working.pluginId
            val existingAlias = pluginId?.let(pluginsById::get)
            if (existingAlias != null) {
                mergedPlugins[existingAlias] = mergePluginEntry(
                    existing = mergedPlugins.getValue(existingAlias),
                    incoming = working,
                    versionMap = mergedVersions
                )
                return@forEach
            }

            val finalAlias = if (pluginAliases.add(working.alias)) {
                working.alias
            } else {
                generateUniqueAlias(
                    preferredAlias = working.preferredAlias(),
                    occupiedAliases = pluginAliases
                )
            }
            working.alias = finalAlias
            mergedPlugins[finalAlias] = working
            working.pluginId?.takeIf { it.isNotBlank() }?.let { pluginsById[it] = finalAlias }
        }

        val mergedBundles = linkedMapOf<String, MutableList<String>>()
        existing.bundles.forEach { (key, aliases) ->
            mergedBundles[key] = aliases.toMutableList()
        }
        imported.bundles.forEach { (key, aliases) ->
            val rewrittenAliases = aliases
                .map { alias -> libraryAliasRedirects[alias] ?: alias }
                .distinct()
            val target = mergedBundles.getOrPut(key) { mutableListOf() }
            rewrittenAliases.forEach { alias ->
                if (alias !in target) {
                    target += alias
                }
            }
        }

        val mergedOtherSections = buildMergedOtherSections(existing.otherSections, imported.otherSections)
        val mergedPreamble = when {
            existing.preamble.isNotBlank() -> existing.preamble
            imported.preamble.isNotBlank() -> imported.preamble
            else -> ""
        }

        val mergedContent = buildCatalogContent(
            preamble = mergedPreamble,
            versions = mergedVersions,
            libraries = mergedLibraries,
            plugins = mergedPlugins,
            bundles = mergedBundles,
            otherSections = mergedOtherSections
        )

        return MergeResult(
            content = mergedContent,
            summary = MergeSummary(
                versionsChanged = countVersionChanges(existing.versions, mergedVersions),
                librariesChanged = countLibraryChanges(existing.libraries, mergedLibraries),
                pluginsChanged = countPluginChanges(existing.plugins, mergedPlugins),
                bundlesChanged = countBundleChanges(existing.bundles, mergedBundles)
            )
        )
    }

    private fun mergeLibraryEntry(
        existing: CatalogLibraryEntry,
        incoming: CatalogLibraryEntry,
        versionMap: MutableMap<String, String>
    ): CatalogLibraryEntry {
        val existingVersion = existing.resolveVersion(versionMap)
        val incomingVersion = incoming.resolveVersion(versionMap)
        val winner = when {
            compareVersions(incomingVersion, existingVersion) > 0 -> incoming
            compareVersions(incomingVersion, existingVersion) < 0 -> existing
            else -> preferStructuredEntry(existing, incoming)
        }
        val merged = winner.copy(alias = existing.alias, inlineFields = LinkedHashMap(winner.inlineFields))
        val bestVersion = pickHigherVersion(existingVersion, incomingVersion)
        return applyResolvedVersion(
            entry = merged,
            preferredVersionRef = existing.versionRef ?: winner.versionRef,
            resolvedVersion = bestVersion,
            versionMap = versionMap
        )
    }

    private fun mergePluginEntry(
        existing: CatalogPluginEntry,
        incoming: CatalogPluginEntry,
        versionMap: MutableMap<String, String>
    ): CatalogPluginEntry {
        val existingVersion = existing.resolveVersion(versionMap)
        val incomingVersion = incoming.resolveVersion(versionMap)
        val winner = when {
            compareVersions(incomingVersion, existingVersion) > 0 -> incoming
            compareVersions(incomingVersion, existingVersion) < 0 -> existing
            else -> preferStructuredEntry(existing, incoming)
        }
        val merged = winner.copy(alias = existing.alias, inlineFields = LinkedHashMap(winner.inlineFields))
        val bestVersion = pickHigherVersion(existingVersion, incomingVersion)
        return applyResolvedVersion(
            entry = merged,
            preferredVersionRef = existing.versionRef ?: winner.versionRef,
            resolvedVersion = bestVersion,
            versionMap = versionMap
        )
    }

    private fun preferStructuredEntry(
        existing: CatalogLibraryEntry,
        incoming: CatalogLibraryEntry
    ): CatalogLibraryEntry {
        return when {
            existing.inlineFields.isEmpty() && incoming.inlineFields.isNotEmpty() -> incoming
            existing.inlineFields.isNotEmpty() && incoming.inlineFields.isEmpty() -> existing
            else -> existing
        }
    }

    private fun preferStructuredEntry(
        existing: CatalogPluginEntry,
        incoming: CatalogPluginEntry
    ): CatalogPluginEntry {
        return when {
            existing.inlineFields.isEmpty() && incoming.inlineFields.isNotEmpty() -> incoming
            existing.inlineFields.isNotEmpty() && incoming.inlineFields.isEmpty() -> existing
            else -> existing
        }
    }

    private fun applyResolvedVersion(
        entry: CatalogLibraryEntry,
        preferredVersionRef: String?,
        resolvedVersion: String?,
        versionMap: MutableMap<String, String>
    ): CatalogLibraryEntry {
        if (!preferredVersionRef.isNullOrBlank() && !resolvedVersion.isNullOrBlank()) {
            versionMap[preferredVersionRef] = pickHigherVersion(versionMap[preferredVersionRef], resolvedVersion)
            entry.versionRef = preferredVersionRef
            entry.directVersion = null
        } else if (!resolvedVersion.isNullOrBlank()) {
            entry.versionRef = null
            entry.directVersion = resolvedVersion
        }
        return entry
    }

    private fun applyResolvedVersion(
        entry: CatalogPluginEntry,
        preferredVersionRef: String?,
        resolvedVersion: String?,
        versionMap: MutableMap<String, String>
    ): CatalogPluginEntry {
        if (!preferredVersionRef.isNullOrBlank() && !resolvedVersion.isNullOrBlank()) {
            versionMap[preferredVersionRef] = pickHigherVersion(versionMap[preferredVersionRef], resolvedVersion)
            entry.versionRef = preferredVersionRef
            entry.directVersion = null
        } else if (!resolvedVersion.isNullOrBlank()) {
            entry.versionRef = null
            entry.directVersion = resolvedVersion
        }
        return entry
    }

    private fun buildMergedOtherSections(
        existing: List<RawSection>,
        imported: List<RawSection>
    ): List<RawSection> {
        if (existing.isEmpty()) {
            return imported
        }
        if (imported.isEmpty()) {
            return existing
        }
        val merged = existing.toMutableList()
        val occupiedNames = existing.map { it.name to it.lines }.toMutableSet()
        imported.forEach { section ->
            if (section.lines.isEmpty() && merged.any { it.name == section.name && it.lines.isEmpty() }) {
                return@forEach
            }
            if (occupiedNames.add(section.name to section.lines)) {
                merged += section
            }
        }
        return merged
    }

    private fun buildCatalogContent(
        preamble: String,
        versions: Map<String, String>,
        libraries: Map<String, CatalogLibraryEntry>,
        plugins: Map<String, CatalogPluginEntry>,
        bundles: Map<String, List<String>>,
        otherSections: List<RawSection>
    ): String {
        val sections = mutableListOf<String>()
        if (preamble.isNotBlank()) {
            sections += preamble.trimEnd()
        }
        if (versions.isNotEmpty()) {
            sections += buildString {
                appendLine("[versions]")
                versions.toSortedMap().forEach { (key, value) ->
                    appendLine("$key = ${quote(value)}")
                }
            }.trimEnd()
        }
        if (libraries.isNotEmpty()) {
            sections += buildString {
                appendLine("[libraries]")
                libraries.toSortedMap().forEach { (_, entry) ->
                    appendLine(renderLibraryEntry(entry))
                }
            }.trimEnd()
        }
        if (bundles.isNotEmpty()) {
            sections += buildString {
                appendLine("[bundles]")
                bundles.toSortedMap().forEach { (key, aliases) ->
                    appendLine("$key = [${aliases.distinct().sorted().joinToString(", ") { alias -> quote(alias) }}]")
                }
            }.trimEnd()
        }
        if (plugins.isNotEmpty()) {
            sections += buildString {
                appendLine("[plugins]")
                plugins.toSortedMap().forEach { (_, entry) ->
                    appendLine(renderPluginEntry(entry))
                }
            }.trimEnd()
        }
        otherSections.forEach { section ->
            sections += buildString {
                appendLine("[${section.name}]")
                section.lines.forEach { line -> appendLine(line) }
            }.trimEnd()
        }
        return sections.joinToString("\n\n").trimEnd() + "\n"
    }

    private fun renderLibraryEntry(entry: CatalogLibraryEntry): String {
        val fallbackRaw = entry.rawValue?.takeIf { it.isNotBlank() }
        if (entry.groupId.isNullOrBlank() || entry.artifactId.isNullOrBlank()) {
            return "${entry.alias} = ${fallbackRaw ?: quote("")}"
        }

        val extras = LinkedHashMap(entry.inlineFields)
        KNOWN_LIBRARY_FIELDS.forEach(extras::remove)

        val fields = linkedMapOf<String, String>()
        fields["group"] = quote(entry.groupId)
        fields["name"] = quote(entry.artifactId)
        val versionRef = entry.versionRef
        val directVersion = entry.directVersion
        if (!versionRef.isNullOrBlank()) {
            fields["version.ref"] = quote(versionRef)
        } else if (!directVersion.isNullOrBlank()) {
            fields["version"] = quote(directVersion)
        }
        extras.forEach { (key, value) ->
            fields.putIfAbsent(key, value)
        }
        return "${entry.alias} = { ${fields.entries.joinToString(", ") { (key, value) -> "$key = $value" }} }"
    }

    private fun renderPluginEntry(entry: CatalogPluginEntry): String {
        val fallbackRaw = entry.rawValue?.takeIf { it.isNotBlank() }
        val pluginId = entry.pluginId?.takeIf { it.isNotBlank() }
        if (pluginId == null) {
            return "${entry.alias} = ${fallbackRaw ?: quote("")}"
        }

        val extras = LinkedHashMap(entry.inlineFields)
        KNOWN_PLUGIN_FIELDS.forEach(extras::remove)

        val fields = linkedMapOf<String, String>()
        fields["id"] = quote(pluginId)
        val versionRef = entry.versionRef
        val directVersion = entry.directVersion
        if (!versionRef.isNullOrBlank()) {
            fields["version.ref"] = quote(versionRef)
        } else if (!directVersion.isNullOrBlank()) {
            fields["version"] = quote(directVersion)
        }
        extras.forEach { (key, value) ->
            fields.putIfAbsent(key, value)
        }
        return "${entry.alias} = { ${fields.entries.joinToString(", ") { (key, value) -> "$key = $value" }} }"
    }

    private fun countVersionChanges(
        existing: Map<String, String>,
        merged: Map<String, String>
    ): Int {
        return merged.count { (key, value) -> existing[key] != value }
    }

    private fun countLibraryChanges(
        existing: Map<String, CatalogLibraryEntry>,
        merged: Map<String, CatalogLibraryEntry>
    ): Int {
        return merged.count { (key, entry) ->
            renderLibraryEntry(existing[key] ?: return@count true) != renderLibraryEntry(entry)
        }
    }

    private fun countPluginChanges(
        existing: Map<String, CatalogPluginEntry>,
        merged: Map<String, CatalogPluginEntry>
    ): Int {
        return merged.count { (key, entry) ->
            renderPluginEntry(existing[key] ?: return@count true) != renderPluginEntry(entry)
        }
    }

    private fun countBundleChanges(
        existing: Map<String, List<String>>,
        merged: Map<String, List<String>>
    ): Int {
        return merged.count { (key, aliases) -> existing[key].orEmpty().distinct() != aliases.distinct() }
    }

    private fun mergeVersionMap(
        target: MutableMap<String, String>,
        source: Map<String, String>
    ) {
        source.forEach { (key, value) ->
            target[key] = pickHigherVersion(target[key], value)
        }
    }

    private fun pickHigherVersion(left: String?, right: String?): String {
        return when {
            left.isNullOrBlank() -> right.orEmpty()
            right.isNullOrBlank() -> left
            compareVersions(left, right) >= 0 -> left
            else -> right
        }
    }

    private fun compareVersions(left: String?, right: String?): Int {
        if (left.isNullOrBlank() && right.isNullOrBlank()) {
            return 0
        }
        if (left.isNullOrBlank()) {
            return -1
        }
        if (right.isNullOrBlank()) {
            return 1
        }
        val leftComparable = toComparableVersion(left)
        val rightComparable = toComparableVersion(right)
        return leftComparable.compareTo(rightComparable)
    }

    private fun toComparableVersion(version: String): String {
        return version.split(Regex("[.\\-+_]"))
            .joinToString(".") { segment ->
                segment.toIntOrNull()?.let { "%010d".format(it) } ?: segment.lowercase()
            }
    }

    private fun parseCatalog(content: String): ParsedCatalog {
        val standardSections = linkedMapOf(
            "versions" to mutableListOf<String>(),
            "libraries" to mutableListOf<String>(),
            "bundles" to mutableListOf<String>(),
            "plugins" to mutableListOf<String>()
        )
        val otherSections = mutableListOf<RawSection>()
        var preamble = mutableListOf<String>()
        var currentSection: String? = null
        var currentLines = mutableListOf<String>()

        fun flushCurrentSection() {
            val lines = currentLines.toList()
            when (currentSection) {
                null -> preamble = lines.toMutableList()
                in standardSections.keys -> standardSections.getValue(currentSection!!).addAll(lines)
                else -> otherSections += RawSection(currentSection!!, lines)
            }
        }

        content.replace("\r\n", "\n").lines().forEach { line ->
            val match = SECTION_HEADER_REGEX.matchEntire(line.trim())
            if (match != null) {
                flushCurrentSection()
                currentSection = match.groupValues[1]
                currentLines = mutableListOf()
            } else {
                currentLines += line
            }
        }
        flushCurrentSection()

        return ParsedCatalog(
            preamble = preamble.joinToString("\n").trimEnd(),
            versions = parseVersions(standardSections.getValue("versions")),
            libraries = parseLibraries(standardSections.getValue("libraries")),
            bundles = parseBundles(standardSections.getValue("bundles")),
            plugins = parsePlugins(standardSections.getValue("plugins")),
            otherSections = otherSections
        )
    }

    private fun parseVersions(lines: List<String>): LinkedHashMap<String, String> {
        val result = linkedMapOf<String, String>()
        parseSectionEntries(lines).forEach { entry ->
            val value = unquote(stripComment(entry.rawValue).trim())
            if (entry.key.isNotBlank() && value.isNotBlank()) {
                result[entry.key] = value
            }
        }
        return result
    }

    private fun parseLibraries(lines: List<String>): LinkedHashMap<String, CatalogLibraryEntry> {
        val result = linkedMapOf<String, CatalogLibraryEntry>()
        parseSectionEntries(lines).forEach { entry ->
            val parsed = parseLibraryEntry(entry)
            result[parsed.alias] = parsed
        }
        return result
    }

    private fun parsePlugins(lines: List<String>): LinkedHashMap<String, CatalogPluginEntry> {
        val result = linkedMapOf<String, CatalogPluginEntry>()
        parseSectionEntries(lines).forEach { entry ->
            val parsed = parsePluginEntry(entry)
            result[parsed.alias] = parsed
        }
        return result
    }

    private fun parseBundles(lines: List<String>): LinkedHashMap<String, List<String>> {
        val result = linkedMapOf<String, List<String>>()
        parseSectionEntries(lines).forEach { entry ->
            val aliases = parseArrayValues(entry.rawValue)
            result[entry.key] = aliases
        }
        return result
    }

    private fun parseLibraryEntry(entry: RawEntry): CatalogLibraryEntry {
        val cleanedValue = stripComment(entry.rawValue).trim()
        val inlineFields = parseInlineTable(cleanedValue)
        if (inlineFields != null) {
            val module = inlineFields["module"]?.let(::unquote)
            val groupId = inlineFields["group"]?.let(::unquote) ?: module?.substringBefore(':')
            val artifactId = inlineFields["name"]?.let(::unquote) ?: module?.substringAfter(':', "")
            val versionRef = inlineFields["version.ref"]?.let(::unquote)
            val directVersion = inlineFields["version"]
                ?.takeIf { value -> !value.trim().startsWith("{") }
                ?.let(::unquote)
            return CatalogLibraryEntry(
                alias = entry.key,
                groupId = groupId?.takeIf { it.isNotBlank() },
                artifactId = artifactId?.takeIf { it.isNotBlank() },
                versionRef = versionRef?.takeIf { it.isNotBlank() },
                directVersion = directVersion?.takeIf { it.isNotBlank() },
                rawValue = cleanedValue,
                inlineFields = inlineFields
            )
        }

        val literal = cleanedValue.takeIf { it.isNotBlank() }
        val coordinates = literal?.let(::unquote)?.split(':').orEmpty()
        return CatalogLibraryEntry(
            alias = entry.key,
            groupId = coordinates.getOrNull(0)?.takeIf { coordinates.size >= 2 },
            artifactId = coordinates.getOrNull(1)?.takeIf { coordinates.size >= 2 },
            versionRef = null,
            directVersion = coordinates.getOrNull(2)?.takeIf { coordinates.size >= 3 },
            rawValue = literal,
            inlineFields = linkedMapOf()
        )
    }

    private fun parsePluginEntry(entry: RawEntry): CatalogPluginEntry {
        val cleanedValue = stripComment(entry.rawValue).trim()
        val inlineFields = parseInlineTable(cleanedValue)
        if (inlineFields != null) {
            val pluginId = inlineFields["id"]?.let(::unquote)
            val versionRef = inlineFields["version.ref"]?.let(::unquote)
            val directVersion = inlineFields["version"]
                ?.takeIf { value -> !value.trim().startsWith("{") }
                ?.let(::unquote)
            return CatalogPluginEntry(
                alias = entry.key,
                pluginId = pluginId?.takeIf { it.isNotBlank() },
                versionRef = versionRef?.takeIf { it.isNotBlank() },
                directVersion = directVersion?.takeIf { it.isNotBlank() },
                rawValue = cleanedValue,
                inlineFields = inlineFields
            )
        }

        val literal = cleanedValue.takeIf { it.isNotBlank() }
        val rawText = literal?.let(::unquote).orEmpty()
        return CatalogPluginEntry(
            alias = entry.key,
            pluginId = rawText.substringBefore(':').takeIf { it.isNotBlank() },
            versionRef = null,
            directVersion = rawText.substringAfter(':', "").takeIf { rawText.contains(':') && it.isNotBlank() },
            rawValue = literal,
            inlineFields = linkedMapOf()
        )
    }

    private fun parseSectionEntries(lines: List<String>): List<RawEntry> {
        val entries = mutableListOf<RawEntry>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith('#')) {
                index++
                continue
            }
            val equalsIndex = line.indexOf('=')
            if (equalsIndex < 0) {
                index++
                continue
            }
            val key = line.substring(0, equalsIndex).trim().trim('"', '\'')
            val valueLines = mutableListOf<String>()
            var braceDepth = 0
            var bracketDepth = 0
            var parenthesisDepth = 0
            var firstLine = true

            while (index < lines.size) {
                val currentLine = lines[index]
                val segment = if (firstLine) currentLine.substring(equalsIndex + 1) else currentLine
                valueLines += segment
                val balance = countBalance(segment)
                braceDepth += balance.braceDelta
                bracketDepth += balance.bracketDelta
                parenthesisDepth += balance.parenthesisDelta
                index++
                firstLine = false
                if (braceDepth <= 0 && bracketDepth <= 0 && parenthesisDepth <= 0) {
                    break
                }
            }

            if (key.isNotBlank()) {
                entries += RawEntry(key = key, rawValue = valueLines.joinToString("\n").trim())
            }
        }
        return entries
    }

    private fun parseInlineTable(rawValue: String): LinkedHashMap<String, String>? {
        val trimmed = rawValue.trim()
        if (!trimmed.startsWith('{') || !trimmed.endsWith('}')) {
            return null
        }
        val body = trimmed.substring(1, trimmed.length - 1)
        val fields = linkedMapOf<String, String>()
        splitTopLevel(body, ',').forEach { part ->
            val normalized = part.trim()
            if (normalized.isEmpty()) {
                return@forEach
            }
            val equalsIndex = findTopLevelEquals(normalized)
            if (equalsIndex < 0) {
                return@forEach
            }
            val key = normalized.substring(0, equalsIndex).trim()
            val value = normalized.substring(equalsIndex + 1).trim()
            if (key.isNotBlank() && value.isNotBlank()) {
                fields[key] = value
            }
        }
        return fields
    }

    private fun parseArrayValues(rawValue: String): List<String> {
        val trimmed = stripComment(rawValue).trim()
        if (!trimmed.startsWith('[') || !trimmed.endsWith(']')) {
            return emptyList()
        }
        val body = trimmed.substring(1, trimmed.length - 1)
        return splitTopLevel(body, ',')
            .mapNotNull { part -> unquote(part.trim()).takeIf { it.isNotBlank() } }
    }

    private fun splitTopLevel(text: String, separator: Char): List<String> {
        if (text.isBlank()) {
            return emptyList()
        }
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inSingleQuote = false
        var inDoubleQuote = false
        var escape = false
        var braceDepth = 0
        var bracketDepth = 0
        var parenthesisDepth = 0

        text.forEach { ch ->
            when {
                escape -> {
                    current.append(ch)
                    escape = false
                }
                ch == '\\' && inDoubleQuote -> {
                    current.append(ch)
                    escape = true
                }
                ch == '\'' && !inDoubleQuote -> {
                    inSingleQuote = !inSingleQuote
                    current.append(ch)
                }
                ch == '"' && !inSingleQuote -> {
                    inDoubleQuote = !inDoubleQuote
                    current.append(ch)
                }
                !inSingleQuote && !inDoubleQuote && ch == '{' -> {
                    braceDepth++
                    current.append(ch)
                }
                !inSingleQuote && !inDoubleQuote && ch == '}' -> {
                    braceDepth--
                    current.append(ch)
                }
                !inSingleQuote && !inDoubleQuote && ch == '[' -> {
                    bracketDepth++
                    current.append(ch)
                }
                !inSingleQuote && !inDoubleQuote && ch == ']' -> {
                    bracketDepth--
                    current.append(ch)
                }
                !inSingleQuote && !inDoubleQuote && ch == '(' -> {
                    parenthesisDepth++
                    current.append(ch)
                }
                !inSingleQuote && !inDoubleQuote && ch == ')' -> {
                    parenthesisDepth--
                    current.append(ch)
                }
                !inSingleQuote && !inDoubleQuote && braceDepth == 0 && bracketDepth == 0 && parenthesisDepth == 0 && ch == separator -> {
                    result += current.toString()
                    current.clear()
                }
                else -> {
                    current.append(ch)
                }
            }
        }
        result += current.toString()
        return result
    }

    private fun findTopLevelEquals(text: String): Int {
        var inSingleQuote = false
        var inDoubleQuote = false
        var escape = false
        var braceDepth = 0
        var bracketDepth = 0
        var parenthesisDepth = 0

        text.forEachIndexed { index, ch ->
            when {
                escape -> escape = false
                ch == '\\' && inDoubleQuote -> escape = true
                ch == '\'' && !inDoubleQuote -> inSingleQuote = !inSingleQuote
                ch == '"' && !inSingleQuote -> inDoubleQuote = !inDoubleQuote
                !inSingleQuote && !inDoubleQuote && ch == '{' -> braceDepth++
                !inSingleQuote && !inDoubleQuote && ch == '}' -> braceDepth--
                !inSingleQuote && !inDoubleQuote && ch == '[' -> bracketDepth++
                !inSingleQuote && !inDoubleQuote && ch == ']' -> bracketDepth--
                !inSingleQuote && !inDoubleQuote && ch == '(' -> parenthesisDepth++
                !inSingleQuote && !inDoubleQuote && ch == ')' -> parenthesisDepth--
                !inSingleQuote && !inDoubleQuote && braceDepth == 0 && bracketDepth == 0 && parenthesisDepth == 0 && ch == '=' -> return index
            }
        }
        return -1
    }

    private fun countBalance(text: String): Balance {
        var inSingleQuote = false
        var inDoubleQuote = false
        var escape = false
        var braceDepth = 0
        var bracketDepth = 0
        var parenthesisDepth = 0

        for (ch in text) {
            when {
                escape -> escape = false
                ch == '\\' && inDoubleQuote -> escape = true
                ch == '\'' && !inDoubleQuote -> inSingleQuote = !inSingleQuote
                ch == '"' && !inSingleQuote -> inDoubleQuote = !inDoubleQuote
                !inSingleQuote && !inDoubleQuote && ch == '#' -> break
                !inSingleQuote && !inDoubleQuote && ch == '{' -> braceDepth++
                !inSingleQuote && !inDoubleQuote && ch == '}' -> braceDepth--
                !inSingleQuote && !inDoubleQuote && ch == '[' -> bracketDepth++
                !inSingleQuote && !inDoubleQuote && ch == ']' -> bracketDepth--
                !inSingleQuote && !inDoubleQuote && ch == '(' -> parenthesisDepth++
                !inSingleQuote && !inDoubleQuote && ch == ')' -> parenthesisDepth--
            }
        }

        return Balance(
            braceDelta = braceDepth,
            bracketDelta = bracketDepth,
            parenthesisDelta = parenthesisDepth
        )
    }

    private fun stripComment(text: String): String {
        val result = StringBuilder()
        var inSingleQuote = false
        var inDoubleQuote = false
        var escape = false
        text.forEach { ch ->
            when {
                escape -> {
                    result.append(ch)
                    escape = false
                }
                ch == '\\' && inDoubleQuote -> {
                    result.append(ch)
                    escape = true
                }
                ch == '\'' && !inDoubleQuote -> {
                    inSingleQuote = !inSingleQuote
                    result.append(ch)
                }
                ch == '"' && !inSingleQuote -> {
                    inDoubleQuote = !inDoubleQuote
                    result.append(ch)
                }
                ch == '#' && !inSingleQuote && !inDoubleQuote -> return result.toString().trimEnd()
                else -> result.append(ch)
            }
        }
        return result.toString().trimEnd()
    }

    private fun quote(value: String): String {
        return "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
    }

    private fun unquote(value: String): String {
        val trimmed = value.trim()
        return when {
            trimmed.length >= 2 && trimmed.startsWith('"') && trimmed.endsWith('"') -> trimmed.substring(1, trimmed.length - 1)
            trimmed.length >= 2 && trimmed.startsWith('\'') && trimmed.endsWith('\'') -> trimmed.substring(1, trimmed.length - 1)
            else -> trimmed
        }
    }

    private fun generateUniqueAlias(
        preferredAlias: String,
        occupiedAliases: MutableSet<String>
    ): String {
        val base = normalizeAlias(preferredAlias.ifBlank { "merged-alias" })
        if (occupiedAliases.add(base)) {
            return base
        }
        var index = 2
        while (true) {
            val candidate = "$base-$index"
            if (occupiedAliases.add(candidate)) {
                return candidate
            }
            index++
        }
    }

    private fun normalizeAlias(alias: String): String {
        return alias
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .replace(Regex("-{2,}"), "-")
            .trim('-')
            .ifBlank { "merged-alias" }
    }

    data class MergeResult(
        val content: String,
        val summary: MergeSummary
    )

    data class MergeSummary(
        val versionsChanged: Int,
        val librariesChanged: Int,
        val pluginsChanged: Int,
        val bundlesChanged: Int
    )

    private data class ParsedCatalog(
        val preamble: String,
        val versions: LinkedHashMap<String, String>,
        val libraries: LinkedHashMap<String, CatalogLibraryEntry>,
        val bundles: LinkedHashMap<String, List<String>>,
        val plugins: LinkedHashMap<String, CatalogPluginEntry>,
        val otherSections: List<RawSection>
    )

    private data class RawSection(
        val name: String,
        val lines: List<String>
    )

    private data class RawEntry(
        val key: String,
        val rawValue: String
    )

    private data class Balance(
        val braceDelta: Int,
        val bracketDelta: Int,
        val parenthesisDelta: Int
    )

    private data class CatalogLibraryEntry(
        var alias: String,
        val groupId: String?,
        val artifactId: String?,
        var versionRef: String?,
        var directVersion: String?,
        val rawValue: String?,
        val inlineFields: LinkedHashMap<String, String>
    ) {
        fun coordinateKey(): String? {
            if (groupId.isNullOrBlank() || artifactId.isNullOrBlank()) {
                return null
            }
            return "$groupId:$artifactId"
        }

        fun preferredAlias(): String {
            if (!groupId.isNullOrBlank() && !artifactId.isNullOrBlank()) {
                return "$groupId-$artifactId"
            }
            return alias
        }

        fun resolveVersion(versionMap: Map<String, String>): String? {
            return when {
                !versionRef.isNullOrBlank() -> versionMap[versionRef]
                !directVersion.isNullOrBlank() -> directVersion
                else -> null
            }
        }
    }

    private data class CatalogPluginEntry(
        var alias: String,
        val pluginId: String?,
        var versionRef: String?,
        var directVersion: String?,
        val rawValue: String?,
        val inlineFields: LinkedHashMap<String, String>
    ) {
        fun preferredAlias(): String {
            return pluginId ?: alias
        }

        fun resolveVersion(versionMap: Map<String, String>): String? {
            return when {
                !versionRef.isNullOrBlank() -> versionMap[versionRef]
                !directVersion.isNullOrBlank() -> directVersion
                else -> null
            }
        }
    }

    private val SECTION_HEADER_REGEX = Regex("^\\[([^]]+)]\\s*(#.*)?$")
    private val KNOWN_LIBRARY_FIELDS = setOf("group", "name", "module", "version", "version.ref")
    private val KNOWN_PLUGIN_FIELDS = setOf("id", "version", "version.ref")
}

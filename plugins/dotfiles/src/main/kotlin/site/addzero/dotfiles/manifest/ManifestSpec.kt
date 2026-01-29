package site.addzero.dotfiles.manifest

data class ManifestSpec(
    val version: String = "1",
    val defaults: ManifestDefaults = ManifestDefaults(),
    val entries: List<ManifestEntry> = emptyList(),
)

data class ManifestDefaults(
    val mode: EntryMode = EntryMode.USER,
    val includeIgnored: Boolean = false,
    val excludeFromGit: Boolean = true,
)

data class ManifestEntry(
    val id: String,
    val path: String,
    val scope: EntryScope = EntryScope.PROJECT_ROOT,
    val mode: EntryMode = EntryMode.USER,
    val includeIgnored: Boolean = false,
    val excludeFromGit: Boolean = true,
)

enum class EntryScope {
    USER_HOME,
    PROJECT_ROOT,
}

enum class EntryMode {
    USER,
    PROJECT,
}

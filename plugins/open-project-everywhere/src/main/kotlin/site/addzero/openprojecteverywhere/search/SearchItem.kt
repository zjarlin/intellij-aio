package site.addzero.openprojecteverywhere.search

sealed interface SearchItem {
    data class Project(
        val title: String,
        val baseTitle: String,
        val subtitle: String,
        val description: String?,
        val categoryLabel: String,
        val kind: SearchResultKind,
        val localPath: String?,
        val cloneUrl: String?,
        val webUrl: String?,
        val directoryName: String,
        val cloneParentRelativePath: String?,
        val titleQualifier: String?
    ) : SearchItem

    data class Hint(
        val title: String,
        val description: String?,
        val action: HintAction,
        val isError: Boolean = false
    ) : SearchItem
}

enum class HintAction {
    OPEN_SETTINGS,
    OPEN_GITHUB_SETTINGS,
    NONE
}

enum class SearchResultKind {
    LOCAL,
    GITHUB,
    GITLAB,
    GITEE,
    CUSTOM
}

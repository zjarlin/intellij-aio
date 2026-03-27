package site.addzero.openprojecteverywhere.search

import site.addzero.openprojecteverywhere.OpenProjectEverywhereBundle
import site.addzero.openprojecteverywhere.settings.OpenProjectEverywhereSettings

enum class SearchScope(
    private val actionNameKey: String
) {
    OWN("search.scope.own"),
    GITHUB_PUBLIC("search.scope.github"),
    GITLAB_PUBLIC("search.scope.gitlab"),
    GITEE_PUBLIC("search.scope.gitee"),
    CUSTOM_PUBLIC("search.scope.custom");

    fun displayName(settings: OpenProjectEverywhereSettings): String {
        return when (this) {
            OWN -> OpenProjectEverywhereBundle.message(actionNameKey)
            GITHUB_PUBLIC -> OpenProjectEverywhereBundle.message("search.category.github")
            GITLAB_PUBLIC -> OpenProjectEverywhereBundle.message("search.category.gitlab")
            GITEE_PUBLIC -> OpenProjectEverywhereBundle.message("search.category.gitee")
            CUSTOM_PUBLIC -> settings.customDisplayName.ifBlank {
                OpenProjectEverywhereBundle.message("settings.custom.display.default")
            }
        }
    }

    fun actionName(settings: OpenProjectEverywhereSettings): String {
        return when (this) {
            CUSTOM_PUBLIC -> OpenProjectEverywhereBundle.message(actionNameKey, displayName(settings))
            else -> OpenProjectEverywhereBundle.message(actionNameKey)
        }
    }
}

package site.addzero.openprojecteverywhere.settings

data class OpenProjectEverywhereState(
    var localProjectsRoot: String = OpenProjectEverywhereDefaults.defaultLocalProjectsRoot(),
    var localProjectsRoots: MutableList<String> = mutableListOf(OpenProjectEverywhereDefaults.defaultLocalProjectsRoot()),
    var localProjectsEnabled: Boolean = true,
    var githubEnabled: Boolean = true,
    var githubAuthMode: String = AuthMode.TOKEN.name,
    var githubUsername: String = "",
    var gitlabEnabled: Boolean = true,
    var gitlabBaseUrl: String = "https://gitlab.com",
    var gitlabAuthMode: String = AuthMode.USERNAME_PASSWORD.name,
    var gitlabUsername: String = "",
    var giteeEnabled: Boolean = true,
    var giteeBaseUrl: String = "https://gitee.com",
    var giteeAuthMode: String = AuthMode.USERNAME_PASSWORD.name,
    var giteeUsername: String = "",
    var customEnabled: Boolean = false,
    var customDisplayName: String = "",
    var customProviderKind: String = ProviderKind.GITLAB.name,
    var customBaseUrl: String = "",
    var customAuthMode: String = AuthMode.USERNAME_PASSWORD.name,
    var customUsername: String = "",
)

package site.addzero.gitee.settings

/**
 * Authentication modes supported by the plugin.
 */
enum class GiteeAuthType(val displayName: String) {
    PASSWORD("Username / Password"),
    TOKEN("Access Token");

    companion object {
        fun fromValue(value: String?): GiteeAuthType {
            return entries.firstOrNull { it.name == value } ?: PASSWORD
        }
    }
}

package site.addzero.dotfiles.model

data class EnvVarSpec(
    val key: String,
    val value: String,
    val scope: EnvScope = EnvScope.USER,
    val isSecret: Boolean = false,
)

enum class EnvScope {
    USER,
    PROJECT,
}

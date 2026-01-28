package site.addzero.dotfiles.model

data class ConstantSpec(
    val id: String,
    val name: String,
    val value: String,
    val type: String = "String",
    val languages: List<String> = emptyList(),
)

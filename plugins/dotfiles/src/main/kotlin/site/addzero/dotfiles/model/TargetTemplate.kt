package site.addzero.dotfiles.model

data class TargetTemplate(
    val id: String,
    val templateId: String,
    val outputPath: String,
    val language: String,
    val packageName: String? = null,
)

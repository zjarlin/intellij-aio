package site.addzero.dotfiles.model

data class TemplateSpec(
    val id: String,
    val description: String = "",
    val engine: TemplateEngineId = TemplateEngineId.KOTLIN_SCRIPT,
    val file: String,
    val sourceType: TemplateSourceType = TemplateSourceType.LOCAL,
    val sourceUri: String? = null,
    val sourcePath: String? = null,
    val sourceRef: String? = null,
    val cacheTtlSeconds: Long? = null,
    val sha256: String? = null,
)

enum class TemplateEngineId {
    KOTLIN_SCRIPT,
}

enum class TemplateSourceType {
    LOCAL,
    HTTP,
    GIT,
}

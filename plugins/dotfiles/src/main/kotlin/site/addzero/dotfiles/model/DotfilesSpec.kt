package site.addzero.dotfiles.model

data class DotfilesSpec(
    val version: String = "1",
    val env: List<EnvVarSpec> = emptyList(),
    val constants: List<ConstantSpec> = emptyList(),
    val templates: List<TemplateSpec> = emptyList(),
    val targets: List<TargetTemplate> = emptyList(),
)

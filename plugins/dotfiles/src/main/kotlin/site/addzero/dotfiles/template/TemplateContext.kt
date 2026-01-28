package site.addzero.dotfiles.template

import site.addzero.dotfiles.model.ConstantSpec
import site.addzero.dotfiles.model.EnvVarSpec

data class TemplateContext(
    val env: List<EnvVarSpec> = emptyList(),
    val constants: List<ConstantSpec> = emptyList(),
    val vars: Map<String, Any?> = emptyMap(),
)

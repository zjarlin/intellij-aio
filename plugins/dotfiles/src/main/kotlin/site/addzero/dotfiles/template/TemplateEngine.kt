package site.addzero.dotfiles.template

import site.addzero.dotfiles.model.TemplateEngineId

interface TemplateEngine {
    val id: TemplateEngineId

    fun render(templateText: String, context: TemplateContext): String
}

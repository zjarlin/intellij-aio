package site.addzero.dotfiles.template

import site.addzero.dotfiles.model.TemplateEngineId
import javax.script.ScriptEngineManager

class KotlinScriptTemplateEngine : TemplateEngine {
    override val id: TemplateEngineId = TemplateEngineId.KOTLIN_SCRIPT

    override fun render(templateText: String, context: TemplateContext): String {
        val engine = ScriptEngineManager(this::class.java.classLoader).getEngineByExtension("kts")
            ?: ScriptEngineManager().getEngineByName("kotlin")
            ?: error("Kotlin script engine not available. Add kotlin-scripting-jsr223 at runtime.")

        val bindings = engine.createBindings().apply {
            put("ctx", context)
            put("env", context.env)
            put("constants", context.constants)
            putAll(context.vars)
        }

        val result = engine.eval(templateText, bindings)
        return result?.toString().orEmpty()
    }
}

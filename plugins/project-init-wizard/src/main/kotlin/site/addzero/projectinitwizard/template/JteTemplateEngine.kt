package site.addzero.projectinitwizard.template

import java.io.File
import java.util.concurrent.ConcurrentHashMap

class JteTemplateEngine(private val templateDir: String) {

    private val cache = ConcurrentHashMap<String, String>()

    fun render(templateContent: String, variables: Map<String, Any>): String {
        return renderTemplateContent(templateContent, variables)
    }

    private fun renderTemplateContent(content: String, variables: Map<String, Any>): String {
        var result = content
        
        // Replace ${variableName} patterns with actual values
        for ((key, value) in variables) {
            result = result.replace("\${$key}", value.toString())
            result = result.replace("\${{$key}}", value.toString())
            result = result.replace("{{{$key}}}", value.toString())
            result = result.replace("{{$key}}", value.toString())
        }
        
        // Handle jte-specific syntax: ${expression}
        result = handleJteExpressions(result, variables)
        
        return result
    }

    private fun handleJteExpressions(content: String, variables: Map<String, Any>): String {
        var result = content
        
        // Replace ${var} with actual value (but skip jte control structures)
        val pattern = """\$\{([^}]+)\}""".toRegex()
        result = pattern.replace(result) { match ->
            val expr = match.groupValues[1]
            when {
                // Skip jte control structures
                expr.startsWith("for") || expr.startsWith("if") || 
                expr.startsWith("import") || expr.startsWith("param") ||
                expr.startsWith("end") || expr.trim().startsWith("!") -> match.value
                // Replace variable references
                variables.containsKey(expr.trim()) -> variables[expr.trim()].toString()
                // Keep other expressions as-is
                else -> match.value
            }
        }
        
        return result
    }

    fun renderFile(templateFile: File, variables: Map<String, Any>): String {
        if (!templateFile.exists()) {
            throw IllegalArgumentException("Template file not found: ${templateFile.absolutePath}")
        }
        val content = templateFile.readText()
        return renderTemplateContent(content, variables)
    }

    class TemplateParams(private val variables: Map<String, Any>) {
        operator fun get(key: String): Any? = variables[key]
        fun contains(key: String): Boolean = variables.containsKey(key)
    }
}

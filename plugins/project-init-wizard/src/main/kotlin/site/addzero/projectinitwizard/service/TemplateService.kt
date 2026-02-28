package site.addzero.projectinitwizard.service

import org.yaml.snakeyaml.Yaml
import site.addzero.projectinitwizard.model.Template
import site.addzero.projectinitwizard.model.TemplateVariable
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class TemplateService {

    companion object {
        private const val USER_TEMPLATE_DIR = ".project-init-wizard/templates"
        private const val TEMPLATE_YML = "template.yml"
    }

    private val userTemplatePath: Path by lazy {
        Paths.get(System.getProperty("user.home"), USER_TEMPLATE_DIR)
    }

    fun scanAllTemplates(builtInTemplateDir: String): List<Template> {
        val templates = mutableListOf<Template>()

        // Load built-in templates
        templates.addAll(loadBuiltInTemplates(builtInTemplateDir))

        // Load user templates
        templates.addAll(scanUserTemplates())

        return templates.sortedBy { it.name }
    }

    fun scanUserTemplates(): List<Template> {
        val templates = mutableListOf<Template>()
        val userDir = userTemplatePath.toFile()

        if (!userDir.exists() || !userDir.isDirectory) {
            return templates
        }

        userDir.listFiles()?.filter { it.isDirectory }?.forEach { templateDir ->
            try {
                val template = parseTemplateDir(templateDir, isBuiltIn = false)
                if (template != null) {
                    templates.add(template)
                }
            } catch (e: Exception) {
                // Skip invalid template directories
            }
        }

        return templates
    }

    fun loadBuiltInTemplates(builtInTemplateDir: String): List<Template> {
        val templates = mutableListOf<Template>()
        val builtInDir = File(builtInTemplateDir)

        if (!builtInDir.exists() || !builtInDir.isDirectory) {
            return templates
        }

        builtInDir.listFiles()?.filter { it.isDirectory }?.forEach { templateDir ->
            try {
                val template = parseTemplateDir(templateDir, isBuiltIn = true)
                if (template != null) {
                    templates.add(template)
                }
            } catch (e: Exception) {
                // Skip invalid template directories
            }
        }

        return templates
    }

    private fun parseTemplateDir(dir: File, isBuiltIn: Boolean): Template? {
        val name = dir.name

        // Skip hidden directories
        if (name.startsWith(".")) {
            return null
        }

        // Check if directory has any template files
        val hasTemplateFiles = dir.walkTopDown()
            .any { it.isFile && (it.extension == "kte" || it.extension == "kts" || it.extension == "toml" || it.name != "template.yml") }

        if (!hasTemplateFiles) {
            return null
        }

        val templateYml = File(dir, TEMPLATE_YML)
        val variables = if (templateYml.exists()) {
            parseTemplateYml(templateYml)
        } else {
            // Auto-detect variables from template files
            autoDetectVariables(dir)
        }

        val description = variables.firstOrNull()?.description 
            ?: "Template: $name"

        return Template(
            name = name,
            description = description,
            rootDir = dir,
            isBuiltIn = isBuiltIn,
            variables = variables
        )
    }

    private fun parseTemplateYml(ymlFile: File): List<TemplateVariable> {
        return try {
            val yaml = Yaml()
            val data = yaml.load<Map<String, Any>>(ymlFile.readText())
            
            val variablesSection = data["variables"] as? List<Map<String, Any>>
            variablesSection?.map { varMap ->
                TemplateVariable(
                    name = varMap["name"] as? String ?: "",
                    type = varMap["type"] as? String ?: "string",
                    defaultValue = varMap["default"] as? String ?: "",
                    required = varMap["required"] as? Boolean ?: false,
                    description = varMap["description"] as? String ?: ""
                )
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun autoDetectVariables(templateDir: File): List<TemplateVariable> {
        val variables = mutableSetOf<String>()

        templateDir.walkTopDown()
            .filter { it.isFile && it.extension != "yml" && it.extension != "yaml" }
            .forEach { file ->
                try {
                    val content = file.readText()
                    // Find ${variableName} patterns
                    val pattern = """\$\{(\w+)}""".toRegex()
                    pattern.findAll(content).forEach { variables.add(it.groupValues[1]) }
                    
                    // Also find {{variableName}} patterns
                    val pattern2 = """\{\{(\w+)}}""".toRegex()
                    pattern2.findAll(content).forEach { variables.add(it.groupValues[1]) }
                } catch (e: Exception) {
                    // Skip files that can't be read
                }
            }

        return variables.map { name ->
            TemplateVariable(
                name = name,
                type = "string",
                defaultValue = "",
                required = false,
                description = ""
            )
        }
    }

    fun getTemplateFiles(template: Template): List<File> {
        return template.rootDir.walkTopDown()
            .filter { it.isFile && it.name != TEMPLATE_YML }
            .toList()
    }

    fun getUserTemplatePath(): String {
        return userTemplatePath.toString()
    }
}

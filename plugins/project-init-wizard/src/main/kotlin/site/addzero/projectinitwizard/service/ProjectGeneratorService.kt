package site.addzero.projectinitwizard.service

import site.addzero.projectinitwizard.model.Template
import site.addzero.projectinitwizard.template.JteTemplateEngine
import java.io.File
import java.io.IOException

class ProjectGeneratorService {

    fun generateProject(
        template: Template,
        variables: Map<String, Any>,
        targetDir: File,
        projectName: String
    ): File {
        // Create project root directory
        val projectRoot = File(targetDir, projectName)
        
        if (projectRoot.exists()) {
            throw IOException("Directory already exists: ${projectRoot.absolutePath}")
        }
        
        projectRoot.mkdirs()

        // Copy and render template files
        val engine = JteTemplateEngine(template.rootDir.absolutePath)
        
        template.rootDir.listFiles()?.forEach { file ->
            if (file.name != "template.yml") {
                processFile(file, projectRoot, template.rootDir, variables, engine)
            }
        }

        return projectRoot
    }

    private fun processFile(
        sourceFile: File,
        targetDir: File,
        templateRoot: File,
        variables: Map<String, Any>,
        engine: JteTemplateEngine
    ) {
        // Calculate relative path from template root
        val relativePath = sourceFile.relativeTo(templateRoot).path
        
        // Render file name (for .kte -> .kts conversion)
        var renderedPath = renderPath(relativePath, variables)
        
        // Convert .kte extension to .kts if needed
        renderedPath = renderedPath.replace(".kte", ".kts")

        val targetFile = File(targetDir, renderedPath)

        if (sourceFile.isDirectory) {
            targetFile.mkdirs()
            sourceFile.listFiles()?.forEach { child ->
                processFile(child, targetFile, templateRoot, variables, engine)
            }
        } else {
            // Ensure parent directory exists
            targetFile.parentFile?.mkdirs()

            // Read and render file content
            val content = sourceFile.readText()
            val renderedContent = renderContent(content, variables)
            targetFile.writeText(renderedContent)
        }
    }

    private fun renderPath(path: String, variables: Map<String, Any>): String {
        var result = path
        
        // Replace path variables like ${projectName}
        for ((key, value) in variables) {
            result = result.replace("\${$key}", value.toString())
            result = result.replace("\${{$key}}", value.toString())
            result = result.replace("{{{$key}}}", value.toString())
        }
        
        return result
    }

    private fun renderContent(content: String, variables: Map<String, Any>): String {
        var result = content

        // Replace ${variableName} patterns
        val pattern = """\$\{(\w+)}""".toRegex()
        result = pattern.replace(result) { match ->
            val varName = match.groupValues[1]
            variables[varName]?.toString() ?: match.value
        }

        // Replace {{variableName}} patterns
        val pattern2 = """\{\{(\w+)}}""".toRegex()
        result = pattern2.replace(result) { match ->
            val varName = match.groupValues[1]
            variables[varName]?.toString() ?: match.value
        }

        // Replace {{{variableName}}} patterns
        val pattern3 = """\{\{\{(\w+)}}}{1}""".toRegex()
        result = pattern3.replace(result) { match ->
            val varName = match.groupValues[1]
            variables[varName]?.toString() ?: match.value
        }

        return result
    }
}

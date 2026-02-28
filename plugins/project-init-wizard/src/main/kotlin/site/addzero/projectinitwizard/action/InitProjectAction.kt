package site.addzero.projectinitwizard.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import site.addzero.projectinitwizard.service.ProjectGeneratorService
import site.addzero.projectinitwizard.service.TemplateService
import site.addzero.projectinitwizard.ui.TemplateSelectionDialogWrapper
import java.io.File

class InitProjectAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        // Get built-in template directory from plugin resources
        val builtInTemplateDir = findBuiltInTemplatesDir()

        val templateService = TemplateService()
        val generatorService = ProjectGeneratorService()

        // Scan all templates
        val templates = templateService.scanAllTemplates(builtInTemplateDir)

        if (templates.isEmpty()) {
            Messages.showInfoMessage(
                "No templates found.\n\n" +
                "Place your templates in:\n" +
                "${templateService.getUserTemplatePath()}",
                "No Templates Available"
            )
            return
        }

        // Show template selection dialog
        val dialog = TemplateSelectionDialogWrapper(templates, templateService, generatorService)
        
        if (dialog.showAndGet()) {
            Messages.showInfoMessage(
                "Project created successfully!",
                "Success"
            )
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = true
    }
    
    private fun findBuiltInTemplatesDir(): String {
        val pluginClass = this::class.java
        var resourceUrl = pluginClass.getResource("/templates")
        if (resourceUrl != null) {
            val path = resourceUrl.path
            if (path != null && File(path).exists()) {
                return File(path).absolutePath
            }
        }
        
        // Try classpath resources
        val classLoader = pluginClass.classLoader
        val resources = classLoader.getResources("templates")
        while (resources.hasMoreElements()) {
            val url = resources.nextElement()
            val path = url.path
            if (path != null && File(path).exists()) {
                return File(path).absolutePath
            }
        }
        
        return ""
    }
}

package site.addzero.projectinitwizard.wizard

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.ModuleTypeManager
import com.intellij.openapi.roots.ModifiableRootModel
import site.addzero.projectinitwizard.model.Template
import site.addzero.projectinitwizard.service.ProjectGeneratorService
import site.addzero.projectinitwizard.service.TemplateService
import java.io.File

class ProjectInitModuleBuilder : ModuleBuilder() {

    private val templateService = TemplateService()
    private val generatorService = ProjectGeneratorService()
    private var templates: List<Template> = emptyList()

    companion object {
        private const val TEMPLATES_DIR = "/templates"
    }

    override fun getPresentableName(): String = "Project Init Wizard"

    override fun getDescription(): String = "Create a new project from custom template"

    override fun getNodeIcon(): javax.swing.Icon = com.intellij.icons.AllIcons.Nodes.Project

    override fun isAvailable(): Boolean = true

    override fun getModuleType(): ModuleType<*> {
        return ModuleTypeManager.getInstance().findByID("JAVA_MODULE") ?: ModuleType.EMPTY
    }

    override fun setupRootModel(modifiableRootModel: ModifiableRootModel) {
        // Handled by wizard step
    }

    override fun getCustomOptionsStep(
        context: WizardContext,
        parentDisposable: Disposable
    ): ModuleWizardStep? {
        // Try to find built-in templates from plugin classpath
        val builtInTemplateDir = findBuiltInTemplatesDir()
        
        templates = templateService.scanAllTemplates(builtInTemplateDir)
        
        // Always return wizard step (even if no templates, to show error message)
        return ProjectInitWizardStep(templates, generatorService, context)
    }
    
    private fun findBuiltInTemplatesDir(): String {
        // Try multiple approaches to find built-in templates
        
        // Approach 1: Try to get from plugin jar resources
        val pluginClass = this::class.java
        var resourceUrl = pluginClass.getResource(TEMPLATES_DIR)
        if (resourceUrl != null) {
            val path = resourceUrl.path
            // Check if it's a valid file path
            if (path != null && File(path).exists()) {
                return File(path).absolutePath
            }
        }
        
        // Approach 2: Try to find in IDE plugin directory
        val pluginPath = System.getProperty("idea.plugins.path")
        if (pluginPath != null) {
            val pluginDir = File(pluginPath, "project-init-wizard")
            val templatesDir = File(pluginDir, "templates")
            if (templatesDir.exists()) {
                return templatesDir.absolutePath
            }
        }
        
        // Approach 3: Use classpath root to find resources
        val classLoader = pluginClass.classLoader
        val resources = classLoader.getResources(TEMPLATES_DIR)
        while (resources.hasMoreElements()) {
            val url = resources.nextElement()
            val path = url.path
            if (path != null && File(path).exists()) {
                return File(path).absolutePath
            }
        }
        
        // Fallback: return empty string (will only show user templates)
        return ""
    }
}

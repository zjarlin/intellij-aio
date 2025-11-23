package site.addzero.ide.config.ui

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import site.addzero.ide.config.service.PluginSettingsService
import site.addzero.ide.dynamicform.engine.DynamicFormEngine
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.reflect.KClass

class DynamicFormConfigurable<T : Any>(
    private val displayName: String,
    private val dataClass: KClass<T>,
    private val project: Project? = null,
    private val instanceProvider: () -> T
) : Configurable {
    
    private val formEngine = DynamicFormEngine()
    private var formPanel: JPanel? = null
    
    override fun createComponent(): JComponent {
        val instance = instanceProvider()
        formPanel = formEngine.buildForm(dataClass, instance)
        return formPanel!!
    }
    
    override fun isModified(): Boolean =
        formEngine.isModified()
    
    override fun apply() {
        val formData = formEngine.getFormData()
        
        project?.let {
            val settingsService = PluginSettingsService.getInstance(it)
            settingsService.saveConfigData(formData)
        }
        
        formEngine.reset()
    }
    
    override fun reset() {
        super.reset()
        
        project?.let {
            val settingsService = PluginSettingsService.getInstance(it)
            val savedData = settingsService.getConfigData()
            formEngine.setFormData(savedData)
        }
        
        formEngine.reset()
    }
    
    override fun getDisplayName(): String = displayName
    
    override fun disposeUIResources() {
        formPanel = null
    }
}

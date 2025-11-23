package site.addzero.addl.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.util.Disposer
import site.addzero.ide.dynamicform.engine.DynamicFormEngine
import javax.swing.JComponent
import javax.swing.JPanel

class MyPluginConfigurable : Configurable, Disposable {
    
    private var settings = MyPluginSettingsService.getInstance().state
    private val formEngine = DynamicFormEngine()
    private var formPanel: JPanel? = null
    
    override fun createComponent(): JComponent {
        formPanel = formEngine.buildForm(MyPluginSettings::class, settings)
        Disposer.register(this, this)
        return formPanel!!
    }
    
    override fun isModified(): Boolean =
        formEngine.isModified()
    
    override fun apply() {
        formEngine.getFormData()
            .forEach { (fieldName, value) ->
                runCatching {
                    MyPluginSettings::class.java
                        .getDeclaredField(fieldName)
                        .apply { isAccessible = true }
                        .set(settings, value)
                }.onFailure { it.printStackTrace() }
            }
        
        formEngine.reset()
    }
    
    override fun reset() {
        super.reset()
        formEngine.reset()
    }
    
    override fun getDisplayName(): String = "AutoDDL设置"
    
    override fun dispose() {
        formPanel = null
    }
}

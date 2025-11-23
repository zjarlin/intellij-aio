package site.addzero.ide.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import site.addzero.ide.dynamicform.engine.DynamicFormEngine
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.reflect.KClass

/**
 * 动态表单配置基类
 * 
 * 使用动态表单引擎自动生成UI，无需手写Swing代码
 * 
 * @param T 配置数据类类型
 * @param displayName 显示名称
 * @param dataClass 数据类的KClass
 * @param project 项目实例（可选）
 * @param settingsProvider 提供配置实例的lambda
 * @param onApply 应用配置的回调
 */
abstract class DynamicConfigurable<T : Any>(
    private val displayName: String,
    private val dataClass: KClass<T>,
    private val project: Project? = null,
    private val settingsProvider: () -> T,
    private val onApply: (Map<String, Any?>) -> Unit
) : Configurable, Disposable {
    
    private val formEngine = DynamicFormEngine()
    private var formPanel: JPanel? = null
    
    override fun createComponent(): JComponent {
        val settings = settingsProvider()
        formPanel = formEngine.buildForm(dataClass, settings)
        Disposer.register(this, this)
        return formPanel!!
    }
    
    override fun isModified(): Boolean =
        formEngine.isModified()
    
    override fun apply() {
        val formData = formEngine.getFormData()
        onApply(formData)
        formEngine.reset()
    }
    
    override fun reset() {
        super.reset()
        formEngine.reset()
    }
    
    override fun getDisplayName(): String = displayName
    
    override fun dispose() {
        formPanel = null
    }
}

/**
 * 简化的创建函数
 */
inline fun <reified T : Any> createDynamicConfigurable(
    displayName: String,
    project: Project? = null,
    noinline settingsProvider: () -> T,
    noinline onApply: (Map<String, Any?>) -> Unit
): DynamicConfigurable<T> {
    return object : DynamicConfigurable<T>(
        displayName = displayName,
        dataClass = T::class,
        project = project,
        settingsProvider = settingsProvider,
        onApply = onApply
    ) {}
}

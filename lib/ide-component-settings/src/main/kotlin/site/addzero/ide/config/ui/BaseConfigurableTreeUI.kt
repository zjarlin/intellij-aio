package site.addzero.ide.config.ui

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.JBScrollPane
import site.addzero.ide.config.annotation.SettingRoute
import site.addzero.ide.config.registry.ConfigRegistry
import site.addzero.ide.config.registry.ConfigRouteInfo
import site.addzero.ide.config.service.PluginSettingsService
import site.addzero.ide.ui.form.DynamicFormBuilder
import kotlin.reflect.full.findAnnotation
import java.awt.BorderLayout
import javax.swing.*

/**
 * 基础的可配置树形设置面板类
 * 支持自定义显示名称和配置扫描逻辑
 */
abstract class BaseConfigurableTreeUI(
    protected val labelName: String,
    protected val project: Project? = null,

    protected var configScanner: () -> Unit = {
        // 默认扫描逻辑
        // ConfigScanner.scanAndRegisterConfigs()
    }
) : Configurable {

    private var mainPanel: JPanel? = null
    private val formBuilders = mutableListOf<DynamicFormBuilder>()
    
    override fun getDisplayName(): @NlsContexts.ConfigurableName String? {
        return labelName
    }

    init {
        // 执行配置扫描
        configScanner()
    }

    // 移除了显式的 getDisplayName() 方法，因为属性的 getter 已经提供了相同的功能

    override fun getHelpTopic(): String? = null

    override fun createComponent(): JComponent? {
        if (mainPanel == null) {
            val configs = ConfigRegistry.getRegisteredConfigs()
            
            if (configs.isEmpty()) {
                // 没有配置项时显示提示
                mainPanel = JPanel(BorderLayout())
                mainPanel?.add(JLabel("没有可用的配置项"), BorderLayout.CENTER)
            } else {
                // 创建主面板，显示所有配置项
                mainPanel = JPanel(BorderLayout())
                val formPanel = JPanel()
                formPanel.layout = BoxLayout(formPanel, BoxLayout.Y_AXIS)
                
                // 按parent分组，如果有多个配置项，添加分组标题
                val parentGroups = configs.values.groupBy { getParentFromConfig(it) }
                val totalConfigs = configs.size
                
                parentGroups.forEach { (parentName, configList) ->
                    // 只有在有多个配置项时才添加分组标题
                    val showGroupTitle = totalConfigs > 1 && (parentGroups.size > 1 || configList.size > 1)
                    
                    if (showGroupTitle) {
                        val groupTitle = JLabel(parentName)
                        groupTitle.font = groupTitle.font.deriveFont(groupTitle.font.size + 2f)
                        groupTitle.border = BorderFactory.createEmptyBorder(10, 10, 5, 10)
                        formPanel.add(groupTitle)
                    }
                    
                    // 添加每个配置项的表单
                    formBuilders.clear() // 清除旧的formBuilders
                    configList.forEach { configInfo ->
                        val formBuilder = DynamicFormBuilder(configInfo.configItems)
                        formBuilders.add(formBuilder) // 保存formBuilder引用
                        val configPanel = createConfigPanel(configInfo, formBuilder)
                        
                        // 设置边距
                        if (configPanel is JPanel) {
                            configPanel.border = BorderFactory.createEmptyBorder(
                                if (showGroupTitle) 5 else 10, 
                                10, 
                                if (configList.last() == configInfo) 10 else 10, 
                                10
                            )
                        }
                        
                        formPanel.add(configPanel)
                    }
                    
                    // 重置表单到保存的状态
                    reset()
                }
                
                // 包装在滚动面板中
                val scrollPane = JBScrollPane(formPanel)
                scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
                scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
                
                mainPanel?.add(scrollPane, BorderLayout.CENTER)
            }
        }
        return mainPanel
    }

    /**
     * 从配置信息中获取parent名称
     */
    private fun getParentFromConfig(configInfo: ConfigRouteInfo): String {
        // 尝试从注解中获取parent参数
        val annotation = configInfo.configClass.findAnnotation<SettingRoute>()
        return annotation?.parent ?: labelName
    }

    /**
     * 创建配置面板
     */
    open fun createConfigPanel(
        configInfo: ConfigRouteInfo,
        formBuilder: DynamicFormBuilder
    ): JPanel {
        // 直接返回表单面板，标题由外层统一管理
        return formBuilder.build()
    }

    override fun isModified(): Boolean {
        // 检查是否有任何表单被修改
        return formBuilders.any { it.isModified() }
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        // 应用配置更改
        val allFormData = mutableMapOf<String, Any?>()
        
        // 收集所有表单数据
        formBuilders.forEach { formBuilder ->
            val formData = formBuilder.getFormData()
            allFormData.putAll(formData)
        }
        
        // 保存到持久化服务
        if (project != null) {
            val settingsService = PluginSettingsService.getInstance(project)
            settingsService.saveConfigData(allFormData)
        }
        
        // 标记所有表单为未修改
        formBuilders.forEach { it.setModified(false) }
    }

    override fun reset() {
        // 重置配置
        if (project != null) {
            val settingsService = PluginSettingsService.getInstance(project)
            val savedData = settingsService.getConfigData()
            
            // 将保存的数据应用到表单
            formBuilders.forEach { formBuilder ->
                formBuilder.setFormData(savedData)
            }
        }
    }

    override fun disposeUIResources() {
        mainPanel = null
    }
}

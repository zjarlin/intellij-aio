package site.addzero.aiannotator.settings

import com.intellij.openapi.options.Configurable
import javax.swing.*
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets

class AiAnnotatorConfigurable : Configurable {
    private var panel: JPanel? = null
    
    // AI 配置字段
    private var aiProviderField: JComboBox<String>? = null
    private var aiApiKeyField: JTextField? = null
    private var aiModelField: JTextField? = null
    private var aiBaseUrlField: JTextField? = null
    private var temperatureField: JTextField? = null
    
    // 注解模板字段
    private var swaggerAnnotationField: JTextField? = null
    private var excelAnnotationField: JTextField? = null
    private var customAnnotationField: JTextField? = null
    
    // 功能开关
    private var enableAiGuessingCheckbox: JCheckBox? = null
    private var enableBatchProcessingCheckbox: JCheckBox? = null

    override fun getDisplayName(): String = "AI Annotator"

    override fun createComponent(): JComponent? {
        panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = Insets(5, 5, 5, 5)
        var row = 0

        // AI 配置部分
        addSeparator(panel!!, gbc, row++, "AI 配置")
        
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        panel!!.add(JLabel("AI 提供商:"), gbc)
        gbc.gridx = 1
        gbc.weightx = 1.0
        aiProviderField = JComboBox(arrayOf("DeepSeek", "OpenAI", "Ollama", "DashScope"))
        panel!!.add(aiProviderField, gbc)
        row++

        row = addTextField(panel!!, gbc, row, "API Key:", aiApiKeyField.let { 
            aiApiKeyField = JTextField(30)
            aiApiKeyField!!
        })
        
        row = addTextField(panel!!, gbc, row, "模型名称:", aiModelField.let { 
            aiModelField = JTextField(30)
            aiModelField!!
        })
        
        row = addTextField(panel!!, gbc, row, "API Base URL:", aiBaseUrlField.let { 
            aiBaseUrlField = JTextField(30)
            aiBaseUrlField!!
        })
        
        row = addTextField(panel!!, gbc, row, "Temperature (0.0-1.0):", temperatureField.let { 
            temperatureField = JTextField(10)
            temperatureField!!
        })

        // 注解模板部分
        addSeparator(panel!!, gbc, row++, "注解模板")
        
        row = addTextField(panel!!, gbc, row, "Swagger 注解:", swaggerAnnotationField.let { 
            swaggerAnnotationField = JTextField(30)
            swaggerAnnotationField!!
        })
        
        row = addTextField(panel!!, gbc, row, "Excel 注解:", excelAnnotationField.let { 
            excelAnnotationField = JTextField(30)
            excelAnnotationField!!
        })
        
        row = addTextField(panel!!, gbc, row, "自定义注解:", customAnnotationField.let { 
            customAnnotationField = JTextField(30)
            customAnnotationField!!
        })

        // 功能开关部分
        addSeparator(panel!!, gbc, row++, "功能选项")
        
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 2
        enableAiGuessingCheckbox = JCheckBox("启用 AI 推测字段注释")
        panel!!.add(enableAiGuessingCheckbox, gbc)
        row++
        
        gbc.gridy = row
        enableBatchProcessingCheckbox = JCheckBox("启用批量处理")
        panel!!.add(enableBatchProcessingCheckbox, gbc)
        row++

        // 添加填充空间
        gbc.gridy = row
        gbc.weighty = 1.0
        panel!!.add(JPanel(), gbc)

        reset()
        return panel
    }

    private fun addSeparator(panel: JPanel, gbc: GridBagConstraints, row: Int, title: String): Int {
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 2
        gbc.weightx = 1.0
        panel.add(JSeparator(), gbc)
        
        gbc.gridy = row + 1
        val label = JLabel(title)
        label.font = label.font.deriveFont(label.font.style or java.awt.Font.BOLD)
        panel.add(label, gbc)
        
        gbc.gridwidth = 1
        return row + 2
    }

    private fun addTextField(panel: JPanel, gbc: GridBagConstraints, row: Int, labelText: String, field: JTextField): Int {
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        panel.add(JLabel(labelText), gbc)
        
        gbc.gridx = 1
        gbc.weightx = 1.0
        panel.add(field, gbc)
        
        return row + 1
    }

    override fun isModified(): Boolean {
        val settings = AiAnnotatorSettingsService.getInstance().state
        return aiProviderField?.selectedItem != settings.aiProvider ||
               aiApiKeyField?.text != settings.aiApiKey ||
               aiModelField?.text != settings.aiModel ||
               aiBaseUrlField?.text != settings.aiBaseUrl ||
               temperatureField?.text != settings.temperature.toString() ||
               swaggerAnnotationField?.text != settings.swaggerAnnotation ||
               excelAnnotationField?.text != settings.excelAnnotation ||
               customAnnotationField?.text != settings.customAnnotation ||
               enableAiGuessingCheckbox?.isSelected != settings.enableAiGuessing ||
               enableBatchProcessingCheckbox?.isSelected != settings.enableBatchProcessing
    }

    override fun apply() {
        val settings = AiAnnotatorSettingsService.getInstance().state
        settings.aiProvider = aiProviderField?.selectedItem as? String ?: "DeepSeek"
        settings.aiApiKey = aiApiKeyField?.text ?: ""
        settings.aiModel = aiModelField?.text ?: "deepseek-chat"
        settings.aiBaseUrl = aiBaseUrlField?.text ?: "https://api.deepseek.com"
        settings.temperature = temperatureField?.text?.toDoubleOrNull() ?: 0.3
        settings.swaggerAnnotation = swaggerAnnotationField?.text ?: "@Schema(description = \"{}\")"
        settings.excelAnnotation = excelAnnotationField?.text ?: "@ExcelProperty(\"{}\")"
        settings.customAnnotation = customAnnotationField?.text ?: "@ApiModelProperty(value = \"{}\")"
        settings.enableAiGuessing = enableAiGuessingCheckbox?.isSelected ?: true
        settings.enableBatchProcessing = enableBatchProcessingCheckbox?.isSelected ?: true
    }

    override fun reset() {
        val settings = AiAnnotatorSettingsService.getInstance().state
        aiProviderField?.selectedItem = settings.aiProvider
        aiApiKeyField?.text = settings.aiApiKey
        aiModelField?.text = settings.aiModel
        aiBaseUrlField?.text = settings.aiBaseUrl
        temperatureField?.text = settings.temperature.toString()
        swaggerAnnotationField?.text = settings.swaggerAnnotation
        excelAnnotationField?.text = settings.excelAnnotation
        customAnnotationField?.text = settings.customAnnotation
        enableAiGuessingCheckbox?.isSelected = settings.enableAiGuessing
        enableBatchProcessingCheckbox?.isSelected = settings.enableBatchProcessing
    }
}

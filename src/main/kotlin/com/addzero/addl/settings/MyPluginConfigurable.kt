package com.addzero.addl.settings

import com.addzero.addl.autoddlstarter.generator.IDatabaseGenerator
import com.addzero.addl.autoddlstarter.generator.consts.QWEN_1_5B_CODER
import com.addzero.addl.autoddlstarter.generator.consts.QWEN_1_5B_INSTRUCT
import com.addzero.addl.autoddlstarter.generator.consts.QWEN_MAX
import com.addzero.addl.autoddlstarter.generator.consts.QWEN_TURBO
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*

class MyPluginConfigurable : Configurable {

    private var settings = MyPluginSettingsService.getInstance().state
    private lateinit var panel: JPanel
    private lateinit var modelKeyField: JTextField
    private lateinit var modelTypeCombo: JComboBox<String>
    private lateinit var dbTypeCombo: JComboBox<String>

    override fun createComponent(): JPanel {
        panel = JPanel()

        // 使用 GridBagLayout 进行布局
        panel.layout = GridBagLayout()
        val gbc = GridBagConstraints()

        // 统一的样式配置
        setupLayoutConstraints(gbc)

        // 添加表单组件
        addFormItem("模型 Key:", modelKeyField(), gbc, 0)
        addFormItem("模型类型:", modelTypeComboBox(), gbc, 1)
        addFormItem("数据库类型:", dbTypeComboBox(), gbc, 2)

        return panel
    }

    /**
     * 设置布局约束
     */
    private fun setupLayoutConstraints(gbc: GridBagConstraints) {
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.anchor = GridBagConstraints.WEST
        gbc.insets = Insets(10, 10, 10, 10) // 设置每个组件的内边距
        gbc.weightx = 1.0 // 使文本框和下拉框横向填充
    }

    /**
     * 添加表单项
     */
    private fun addFormItem(labelText: String, component: JComponent, gbc: GridBagConstraints, row: Int) {
        gbc.gridx = 0
        gbc.gridy = row
        panel.add(JLabel(labelText), gbc)

        gbc.gridx = 1
        panel.add(component, gbc)
    }

    /**
     * 创建模型 Key 输入框
     */
    private fun modelKeyField(): JTextField {
        modelKeyField = JTextField(settings.modelKey)
        return modelKeyField
    }

    /**
     * 创建模型类型下拉框
     */
    private fun modelTypeComboBox(): JComboBox<String> {
        modelTypeCombo = ComboBox(
            arrayOf(
                QWEN_TURBO,
                QWEN_1_5B_INSTRUCT,
                QWEN_1_5B_CODER,
                QWEN_MAX
            )
        )
        modelTypeCombo.selectedItem = settings.modelType
        return modelTypeCombo
    }

    /**
     * 创建数据库类型下拉框
     */
    private fun dbTypeComboBox(): JComboBox<String> {
        val databaseType = IDatabaseGenerator.databaseType.map { it.key }.toTypedArray()
        dbTypeCombo = ComboBox(databaseType)
        dbTypeCombo.selectedItem = settings.dbType
        return dbTypeCombo
    }


    override fun isModified(): Boolean {
        return modelKeyField.text != settings.modelKey ||
                modelTypeCombo.selectedItem != settings.modelType ||
                dbTypeCombo.selectedItem != settings.dbType
    }

    override fun apply() {
        settings.modelKey = modelKeyField.text
        settings.modelType = modelTypeCombo.selectedItem as String
        settings.dbType = dbTypeCombo.selectedItem as String
    }

    override fun getDisplayName(): String {
        return "My Plugin Settings"
    }
}
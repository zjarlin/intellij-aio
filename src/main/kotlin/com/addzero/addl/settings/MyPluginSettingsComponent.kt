package com.addzero.addl.settings

import Description
import MyPluginSettings
import cn.hutool.core.util.ReflectUtil
import com.intellij.openapi.components.Service
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.BorderFactory
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

@Service
class MyPluginSettingsComponent {
    val panel: JPanel = JPanel(GridBagLayout())

    init {
        val settings = MyPluginSettings.instance.state // 获取设置实例
        val fields = settings::class.java.declaredFields // 获取所有字段

        val constraints = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0 // 组件占据水平空间
        }

        // 动态生成表单
        fields.forEachIndexed { index, field ->
            field.isAccessible = true
            val annotation = field.getAnnotation(Description::class.java)
            val description = annotation?.description ?: field.name
            val defaultValue = annotation?.defaultValue ?: ""
            val type = annotation?.type ?: FieldType.TEXT

            val label = JLabel(description)

            // 根据注解的 type 动态渲染不同组件
            val component = when (type) {
                FieldType.TEXT -> JTextField(20).apply {
                    name = field.name
                    text = defaultValue // 文本框默认值
                }
                FieldType.DROPDOWN -> JComboBox<String>(annotation.options).apply {
                    name = field.name
                    selectedItem = defaultValue // 下拉框默认选项
                }
            }

            // 添加到面板
            constraints.gridx = 0
            constraints.gridy = index
            panel.add(label, constraints)

            constraints.gridx = 1
            panel.add(component, constraints)
        }

        // 设置面板背景和边框
        panel.border = BorderFactory.createTitledBorder("插件设置")
    }

    // 提供设置值的方法
    fun getSettings(): Settings {
        val settings = MyPluginSettings.instance.state
        val fields = settings::class.java.declaredFields
        fields.forEach { field ->
            field.isAccessible = true
            val component = panel.components
                .filter { it.name == field.name }
                .firstOrNull()

            when (component) {
                is JTextField -> field.set(settings, component.text)
                is JComboBox<*> -> field.set(settings, component.selectedItem)
            }
        }
        return settings
    }
}
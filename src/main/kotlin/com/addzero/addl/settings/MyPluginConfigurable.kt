package com.addzero.addl.settings

import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.text.Document
import javax.swing.*
import kotlin.reflect.full.createInstance
import javax.swing.event.DocumentEvent
class MyPluginConfigurable : Configurable {

    private var settings = MyPluginSettingsService.getInstance().state
    private lateinit var panel: JPanel
    private val components = mutableMapOf<String, JComponent>()
    private val dependentComponents = mutableMapOf<String, Pair<JComponent, FieldDependencyPredicate>>()
    private fun refreshDependentComponents(dependencyField: String, component: JComponent) {
        val dependentPair = dependentComponents[dependencyField] ?: return
        val (dependentComboBox, predicate) = dependentPair

        // 获取依赖字段的当前值并计算下拉选项
        val dependentValue = when (component) {
            is JTextField -> component.text
            is JComboBox<*> -> component.selectedItem as? String
            else -> null
        }

        // 确保 dependentValue 不为 null，提供一个默认值
        val newOptions = predicate.getOptions(dependentValue ?: "")

        // 更新下拉框选项
        if (dependentComboBox is JComboBox<*>) {
            dependentComboBox.removeAllItems()
            newOptions.forEach { dependentComboBox.addItem(it as Nothing?) } // 强制转换为 String
        }
    }


    override fun createComponent(): JPanel {
        panel = JPanel()
        panel.layout = GridBagLayout()
        val gbc = GridBagConstraints()
        setupLayoutConstraints(gbc)

        for (field in MyPluginSettings::class.java.declaredFields) {
            val annotation = field.getAnnotation(ConfigField::class.java) ?: continue
            val label = JLabel(annotation.label)

            val fieldValue = field.get(settings) as? String
            val component = when (annotation.type) {
                FieldType.TEXT -> JTextField(fieldValue ?: "")
                FieldType.DROPDOWN -> {
                    val items = annotation.options
                    val comboBox = ComboBox(items)
                    comboBox.selectedItem = fieldValue


                 // 处理依赖关系
//                    extracted(annotation, comboBox)
                    comboBox
                    }

                FieldType.LONG_TEXT -> JTextArea(fieldValue ?: "")
            }

            addFormItem(label, component, gbc)
            components[field.name] = component

        }

        return panel
    }

    private fun extracted(
        annotation: ConfigField,
        comboBox: ComboBox<String>,
    ) {
        if (annotation.dependsOn.isNotEmpty()) {
            val predicate = annotation.predicateClass.createInstance()
            val pair = comboBox to predicate
            dependentComponents[annotation.dependsOn] = pair
            components[annotation.dependsOn] = comboBox
            // 监听依赖字段变化
            val dependencyField = annotation.dependsOn
            val dependencyComponent = components[dependencyField]
            if (dependencyComponent is JTextField) {
                val document: Document = dependencyComponent.document
                document.addDocumentListener(object : DocumentListener, javax.swing.event.DocumentListener {

                    override fun changedUpdate(e: DocumentEvent) {
                        refreshDependentComponents(dependencyField, dependencyComponent)
                    }

                    override fun removeUpdate(e: DocumentEvent) {
                        refreshDependentComponents(dependencyField, dependencyComponent)
                    }

                    override fun insertUpdate(e: DocumentEvent) {
                        refreshDependentComponents(dependencyField, dependencyComponent)
                    }
                })
            } else if (dependencyComponent is JComboBox<*>) {
                dependencyComponent.addActionListener {
                    refreshDependentComponents(dependencyField, dependencyComponent)
                }
            }
        }
    }


    private fun setupLayoutConstraints(gbc: GridBagConstraints) {
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = Insets(10, 10, 10, 10)
        gbc.weightx = 1.0
    }

    private fun addFormItem(label: JLabel, component: JComponent, gbc: GridBagConstraints) {
        gbc.gridx = 0
        gbc.gridy++
        panel.add(label, gbc)

        gbc.gridx = 1
        panel.add(component, gbc)
    }

    override fun isModified(): Boolean {
        return components.any { (fieldName, component) ->
            val field = MyPluginSettings::class.java.getDeclaredField(fieldName)
            field.isAccessible = true
            val currentValue = field.get(settings)
            val newValue = when (component) {
                is JTextField -> component.text
                is JComboBox<*> -> component.selectedItem ?: "" as String
                is JTextArea -> component.text
                else -> null
            }
            currentValue != newValue
        }
    }

    override fun apply() {
        components.forEach { (fieldName, component) ->
            val field = MyPluginSettings::class.java.getDeclaredField(fieldName)
            field.isAccessible = true
            val newValue = when (component) {
                is JTextField -> component.text
                is JComboBox<*> -> component.selectedItem as String
                is JTextArea -> component.text
                else -> null
            }
            field.set(settings, newValue)
        }
    }

    override fun getDisplayName(): String = "My Plugin Settings"
}
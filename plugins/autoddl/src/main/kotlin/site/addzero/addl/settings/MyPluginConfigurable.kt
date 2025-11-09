package site.addzero.addl.settings

import site.addzero.common.kt_util.isNotNull
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*
import javax.swing.border.TitledBorder
import javax.swing.event.DocumentEvent
import javax.swing.text.Document
import kotlin.reflect.full.createInstance

class MyPluginConfigurable : Configurable, Disposable {
    private var settings = MyPluginSettingsService.getInstance().state
    private lateinit var panel: JPanel
    private val components = mutableMapOf<String, JComponent>()
    private val dependentComponents = mutableMapOf<String, Pair<JComponent, FieldDependencyPredicate>>()
    private val disposableManager = DisposableManager()

    private class DisposableManager : Disposable {
        private val documentListeners = mutableListOf<Pair<Document, javax.swing.event.DocumentListener>>()
        private val actionListeners = mutableListOf<Pair<JComboBox<*>, java.awt.event.ActionListener>>()

        fun addDocumentListener(document: Document, listener: javax.swing.event.DocumentListener) {
            document.addDocumentListener(listener)
            documentListeners.add(document to listener)
        }

        fun addActionListener(comboBox: JComboBox<*>, listener: java.awt.event.ActionListener) {
            comboBox.addActionListener(listener)
            actionListeners.add(comboBox to listener)
        }

        override fun dispose() {
            documentListeners.forEach { (document, listener) ->
                try {
                    document.removeDocumentListener(listener)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            actionListeners.forEach { (comboBox, listener) ->
                try {
                    comboBox.removeActionListener(listener)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            documentListeners.clear()
            actionListeners.clear()
        }
    }

    override fun createComponent(): JPanel {
        panel = JPanel(GridBagLayout())
        val mainGbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            gridx = 0
            insets = JBUI.insets(5)
        }

        // 获取类上的分组注解
        val settingsGroup = MyPluginSettings::class.java.getAnnotation(SettingsGroup::class.java)
        val groups = settingsGroup?.groups?.sortedBy { it.order } ?: emptyList()

        // 获取所有字段及其注解
        val fields = MyPluginSettings::class.java.declaredFields
            .mapNotNull { field ->
                val annotation = field.getAnnotation(ConfigField::class.java)
                if (annotation != null) {
                    Triple(field, annotation, field.get(settings) as? String)
                } else null
            }
            .groupBy { it.second.group }

        // 为每个分组创建面板
        groups.forEach { group ->
            val groupPanel = createGroupPanel(group.title)
            val groupFields = fields[group.name] ?: emptyList()

            // 按 order 排序字段
            val sortedFields = groupFields.sortedBy { it.second.order }

            val gbc = GridBagConstraints().apply {
                fill = GridBagConstraints.HORIZONTAL
                weightx = 1.0
                gridx = 0
                insets = JBUI.insets(2, 5)
            }

            sortedFields.forEach { (field, annotation, value) ->
                val label = JLabel(annotation.label)
                val component = createComponent1(annotation, value)

                addFormItem(groupPanel, label, component, gbc)
                components[field.name] = component
            }

            mainGbc.gridy++
            panel.add(groupPanel, mainGbc)
        }

        // 注册到应用程序级别的 Disposer
        val application = ApplicationManager.getApplication()
            Disposer.register(this, disposableManager)

        return panel
    }

    private fun createGroupPanel(title: String): JPanel {
        return JPanel(GridBagLayout()).apply {
            border = TitledBorder(title)
        }
    }

    private fun createComponent1(annotation: ConfigField, value: String?): JComponent {
        return when (annotation.type) {
            FieldType.TEXT -> JTextField(value ?: "")
            FieldType.DROPDOWN -> {
                val comboBox = ComboBox(annotation.options)
                comboBox.selectedItem = value
                setupDependency(annotation, comboBox)
                comboBox
            }
            FieldType.LONG_TEXT -> JTextArea(value ?: "").apply {
                rows = 3
            }
            FieldType.SEPARATOR -> JSeparator()
        }
    }

    private fun addFormItem(panel: JPanel, label: JLabel, component: JComponent, gbc: GridBagConstraints) {
        if (component is JSeparator) {
            gbc.gridwidth = GridBagConstraints.REMAINDER
            panel.add(component, gbc)
            gbc.gridwidth = 1
        } else {
            gbc.gridx = 0
            gbc.gridy++
            panel.add(label, gbc)

            gbc.gridx = 1
            if (component is JTextArea) {
                val scrollPane = JBScrollPane(component)
                panel.add(scrollPane, gbc)
            } else {
                panel.add(component, gbc)
            }
        }
    }

    private fun setupDependency(annotation: ConfigField, comboBox: ComboBox<String>) {
        if (annotation.dependsOn.isNotEmpty()) {
            val predicate = annotation.predicateClass.createInstance()
            val pair = comboBox to predicate
            dependentComponents[annotation.dependsOn] = pair
            components[annotation.dependsOn] = comboBox

            val dependencyField = annotation.dependsOn
            val dependencyComponent = components[dependencyField]
            if (dependencyComponent is JTextField) {
                val document = dependencyComponent.document
                val listener = object : javax.swing.event.DocumentListener {
                    override fun changedUpdate(e: DocumentEvent) {
                        refreshDependentComponents(dependencyField, dependencyComponent)
                    }

                    override fun removeUpdate(e: DocumentEvent) {
                        refreshDependentComponents(dependencyField, dependencyComponent)
                    }

                    override fun insertUpdate(e: DocumentEvent) {
                        refreshDependentComponents(dependencyField, dependencyComponent)
                    }
                }
                disposableManager.addDocumentListener(document, listener)
            } else if (dependencyComponent is JComboBox<*>) {
                val listener = java.awt.event.ActionListener {
                    refreshDependentComponents(dependencyField, dependencyComponent)
                }
                disposableManager.addActionListener(dependencyComponent, listener)
            }
        }
    }

    private fun refreshDependentComponents(dependencyField: String, component: JComponent) {
        val dependentPair = dependentComponents[dependencyField] ?: return
        val (dependentComboBox, predicate) = dependentPair

        val dependentValue = when (component) {
            is JTextField -> component.text
            is JComboBox<*> -> component.selectedItem as? String
            else -> null
        }

        val newOptions = predicate.getOptions(dependentValue ?: "")

        if (dependentComboBox is JComboBox<*>) {
            dependentComboBox.removeAllItems()
            newOptions.forEach { dependentComboBox.addItem(it as Nothing?) }
        }
    }

    override fun isModified(): Boolean {
        return components.any { (fieldName, component) ->
            val field = MyPluginSettings::class.java.getDeclaredField(fieldName)
            field.isAccessible = true
            val currentValue = field.get(settings)
            val newValue = when (component) {
                is JTextField -> component.text
                is JComboBox<*> -> component.selectedItem ?: ""
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

    override fun getDisplayName(): String = "AutoDDL设置"
    override fun dispose() {
        dependentComponents.clear()
        components.clear()

    }
}

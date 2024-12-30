package com.addzero.addl.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
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
                document.removeDocumentListener(listener)
            }
            actionListeners.forEach { (comboBox, listener) ->
                comboBox.removeActionListener(listener)
            }
            documentListeners.clear()
            actionListeners.clear()
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
                    setupDependency(annotation, comboBox)
                    comboBox
                }
                FieldType.LONG_TEXT -> JTextArea(fieldValue ?: "")
            }

            addFormItem(label, component, gbc)
            components[field.name] = component
        }

        // 注册到应用程序级别的Disposer
        Disposer.register(ApplicationManager.getApplication(), disposableManager)

        return panel
    }

    private fun setupDependency(
        annotation: ConfigField,
        comboBox: ComboBox<String>
    ) {
        if (annotation.dependsOn.isNotEmpty()) {
            val predicate = annotation.predicateClass.createInstance()
            val pair = comboBox to predicate
            dependentComponents[annotation.dependsOn] = pair
            components[annotation.dependsOn] = comboBox

            val dependencyField = annotation.dependsOn
            val dependencyComponent = components[dependencyField]
            if (dependencyComponent is JTextField) {
                val document: Document = dependencyComponent.document
                val listener = object : DocumentListener, javax.swing.event.DocumentListener {
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
}
package site.addzero.ide.dynamicform.engine

import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import site.addzero.ide.dynamicform.model.FormDescriptor
import site.addzero.ide.dynamicform.model.FormGroupDescriptor
import site.addzero.ide.dynamicform.model.RenderedField
import site.addzero.ide.dynamicform.parser.FormDescriptorParser
import site.addzero.ide.dynamicform.renderer.FieldRendererRegistry
import site.addzero.ide.dynamicform.validation.ValidationEngine
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*
import javax.swing.border.TitledBorder
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

class DynamicFormEngine(
    private val parser: FormDescriptorParser = FormDescriptorParser(),
    private val rendererRegistry: FieldRendererRegistry = FieldRendererRegistry.getInstance(),
    private val validationEngine: ValidationEngine = ValidationEngine()
) {
    
    private val renderedFields = mutableMapOf<String, RenderedField>()
    private var isModified = false
    
    fun <T : Any> buildForm(dataClass: KClass<T>, instance: T? = null): JPanel {
        val descriptor = parser.parse(dataClass)
        return buildFormFromDescriptor(descriptor, instance)
    }
    
    fun <T : Any> buildFormFromDescriptor(
        descriptor: FormDescriptor,
        instance: T? = null
    ): JPanel {
        val mainPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            gridx = 0
            gridy = 0
            insets = JBUI.insets(5)
        }
        
        descriptor.groups
            .sortedBy { it.order }
            .forEach { group ->
                val groupPanel = createGroupPanel(group, instance)
                mainPanel.add(groupPanel, gbc)
                gbc.gridy++
            }
        
        val scrollPane = JBScrollPane(mainPanel).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }
        
        val containerPanel = JPanel(BorderLayout())
        containerPanel.add(scrollPane, BorderLayout.CENTER)
        
        return containerPanel
    }
    
    private fun <T : Any> createGroupPanel(
        group: FormGroupDescriptor,
        instance: T?
    ): JPanel {
        val groupPanel = JPanel(GridBagLayout())
        
        if (group.title.isNotEmpty()) {
            groupPanel.border = TitledBorder(group.title)
        }
        
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            gridx = 0
            gridy = 0
            insets = JBUI.insets(2, 5)
        }
        
        group.fields
            .sortedBy { it.order }
            .forEach { fieldDescriptor ->
                val renderedField = rendererRegistry.render(fieldDescriptor)
                renderedFields[fieldDescriptor.name] = renderedField
                
                instance?.let { inst ->
                    inst::class.memberProperties
                        .find { it.name == fieldDescriptor.name }
                        ?.also { property ->
                            property.isAccessible = true
                            val value = property.getter.call(inst)
                            renderedField.setValue(value)
                        }
                }
                
                attachChangeListeners(renderedField)
                
                addFieldToPanel(groupPanel, renderedField, gbc)
                gbc.gridy++
            }
        
        return groupPanel
    }
    
    private fun addFieldToPanel(
        panel: JPanel,
        renderedField: RenderedField,
        gbc: GridBagConstraints
    ) {
        val descriptor = renderedField.descriptor
        
        if (descriptor is site.addzero.ide.dynamicform.model.CheckBoxDescriptor) {
            val checkBox = renderedField.component as JCheckBox
            checkBox.text = buildLabelText(descriptor.label, descriptor.required)
            
            gbc.gridwidth = GridBagConstraints.REMAINDER
            panel.add(checkBox, gbc)
            
            if (descriptor.description.isNotEmpty()) {
                gbc.gridy++
                val descLabel = JLabel("<html><small>${descriptor.description}</small></html>")
                descLabel.border = JBUI.Borders.emptyLeft(20)
                panel.add(descLabel, gbc)
            }
        } else {
            val label = JLabel(buildLabelText(descriptor.label, descriptor.required))
            
            gbc.gridx = 0
            gbc.gridwidth = 1
            gbc.weightx = 0.0
            panel.add(label, gbc)
            
            gbc.gridx = 1
            gbc.weightx = 1.0
            panel.add(renderedField.component, gbc)
            
            if (descriptor.description.isNotEmpty()) {
                gbc.gridx = 1
                gbc.gridy++
                val descLabel = JLabel("<html><small>${descriptor.description}</small></html>")
                panel.add(descLabel, gbc)
            }
        }
    }
    
    private fun buildLabelText(label: String, required: Boolean) =
        if (required) "<html>$label <span color='red'>*</span></html>" else label
    
    private fun attachChangeListeners(renderedField: RenderedField) {
        when (val component = renderedField.component) {
            is JTextField -> component.document.addDocumentListener(createDocumentListener())
            is JTextArea -> component.document.addDocumentListener(createDocumentListener())
            is JPasswordField -> component.document.addDocumentListener(createDocumentListener())
            is JCheckBox -> component.addActionListener { isModified = true }
            is JComboBox<*> -> component.addActionListener { isModified = true }
            is JBScrollPane -> {
                (component.viewport.view as? JTextArea)?.document?.addDocumentListener(createDocumentListener())
            }
        }
    }
    
    private fun createDocumentListener() = object : javax.swing.event.DocumentListener {
        override fun insertUpdate(e: javax.swing.event.DocumentEvent?) { isModified = true }
        override fun removeUpdate(e: javax.swing.event.DocumentEvent?) { isModified = true }
        override fun changedUpdate(e: javax.swing.event.DocumentEvent?) { isModified = true }
    }
    
    fun getFormData(): Map<String, Any?> =
        renderedFields.mapValues { (_, field) -> field.getValue() }
    
    fun setFormData(data: Map<String, Any?>) {
        data.forEach { (name, value) ->
            renderedFields[name]?.setValue(value)
        }
        isModified = false
    }
    
    fun isModified() = isModified
    
    fun validate(): ValidationResult {
        val errors = renderedFields
            .mapNotNull { (name, field) ->
                validationEngine.validate(field.descriptor, field.getValue())
                    ?.let { name to it }
            }
            .toMap()
        
        return ValidationResult(errors.isEmpty(), errors)
    }
    
    fun reset() {
        isModified = false
    }
}

data class ValidationResult(
    val isValid: Boolean,
    val errors: Map<String, String>
)

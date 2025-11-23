package site.addzero.ide.dynamicform.renderer.impl

import com.intellij.ui.components.JBScrollPane
import site.addzero.ide.dynamicform.model.FormFieldDescriptor
import site.addzero.ide.dynamicform.model.RenderedField
import site.addzero.ide.dynamicform.model.TextAreaDescriptor
import site.addzero.ide.dynamicform.renderer.FieldRenderer
import java.awt.Dimension
import javax.swing.JTextArea

class TextAreaRenderer : FieldRenderer<TextAreaDescriptor> {
    
    override fun support(descriptor: FormFieldDescriptor) = 
        descriptor is TextAreaDescriptor
    
    override fun render(descriptor: TextAreaDescriptor): RenderedField {
        val textArea = JTextArea(descriptor.rows, 40).apply {
            descriptor.defaultValue?.toString()?.let { text = it }
            lineWrap = true
            wrapStyleWord = true
            
            if (descriptor.placeholder.isNotEmpty()) {
                toolTipText = descriptor.placeholder
            }
        }
        
        val scrollPane = JBScrollPane(textArea).apply {
            preferredSize = Dimension(600, descriptor.rows * 20 + 20)
        }
        
        return RenderedField(
            descriptor = descriptor,
            component = scrollPane,
            getValue = { textArea.text },
            setValue = { value -> textArea.text = value?.toString() ?: "" }
        )
    }
}

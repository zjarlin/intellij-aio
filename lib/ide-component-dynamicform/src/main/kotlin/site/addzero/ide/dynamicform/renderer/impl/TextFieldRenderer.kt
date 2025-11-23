package site.addzero.ide.dynamicform.renderer.impl

import site.addzero.ide.dynamicform.model.FormFieldDescriptor
import site.addzero.ide.dynamicform.model.RenderedField
import site.addzero.ide.dynamicform.model.TextFieldDescriptor
import site.addzero.ide.dynamicform.renderer.FieldRenderer
import javax.swing.JTextField
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.DocumentFilter

class TextFieldRenderer : FieldRenderer<TextFieldDescriptor> {
    
    override fun support(descriptor: FormFieldDescriptor) = 
        descriptor is TextFieldDescriptor
    
    override fun render(descriptor: TextFieldDescriptor): RenderedField {
        val textField = JTextField(40).apply {
            descriptor.defaultValue?.toString()?.let { text = it }
            
            if (descriptor.placeholder.isNotEmpty()) {
                toolTipText = descriptor.placeholder
            }
            
            if (descriptor.maxLength > 0) {
                (document as? AbstractDocument)?.documentFilter = object : DocumentFilter() {
                    override fun insertString(fb: FilterBypass, offset: Int, string: String, attr: AttributeSet?) {
                        val newLength = fb.document.length + string.length
                        if (newLength <= descriptor.maxLength) {
                            super.insertString(fb, offset, string, attr)
                        }
                    }
                    
                    override fun replace(fb: FilterBypass, offset: Int, length: Int, text: String, attrs: AttributeSet?) {
                        val newLength = fb.document.length - length + text.length
                        if (newLength <= descriptor.maxLength) {
                            super.replace(fb, offset, length, text, attrs)
                        }
                    }
                }
            }
        }
        
        return RenderedField(
            descriptor = descriptor,
            component = textField,
            getValue = { textField.text },
            setValue = { value -> textField.text = value?.toString() ?: "" }
        )
    }
}

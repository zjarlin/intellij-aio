package site.addzero.ide.dynamicform.renderer.impl

import site.addzero.ide.dynamicform.model.FormFieldDescriptor
import site.addzero.ide.dynamicform.model.PasswordFieldDescriptor
import site.addzero.ide.dynamicform.model.RenderedField
import site.addzero.ide.dynamicform.renderer.FieldRenderer
import javax.swing.JPasswordField

class PasswordFieldRenderer : FieldRenderer<PasswordFieldDescriptor> {
    
    override fun support(descriptor: FormFieldDescriptor) = 
        descriptor is PasswordFieldDescriptor
    
    override fun render(descriptor: PasswordFieldDescriptor): RenderedField {
        val passwordField = JPasswordField(40).apply {
            descriptor.defaultValue?.toString()?.let { text = it }
        }
        
        return RenderedField(
            descriptor = descriptor,
            component = passwordField,
            getValue = { String(passwordField.password) },
            setValue = { value -> passwordField.text = value?.toString() ?: "" }
        )
    }
}

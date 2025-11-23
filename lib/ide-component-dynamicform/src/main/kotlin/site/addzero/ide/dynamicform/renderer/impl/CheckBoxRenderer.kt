package site.addzero.ide.dynamicform.renderer.impl

import site.addzero.ide.dynamicform.model.CheckBoxDescriptor
import site.addzero.ide.dynamicform.model.FormFieldDescriptor
import site.addzero.ide.dynamicform.model.RenderedField
import site.addzero.ide.dynamicform.renderer.FieldRenderer
import javax.swing.JCheckBox

class CheckBoxRenderer : FieldRenderer<CheckBoxDescriptor> {
    
    override fun support(descriptor: FormFieldDescriptor) = 
        descriptor is CheckBoxDescriptor
    
    override fun render(descriptor: CheckBoxDescriptor): RenderedField {
        val checkBox = JCheckBox().apply {
            descriptor.defaultValue?.let { 
                isSelected = when (it) {
                    is Boolean -> it
                    is String -> it.toBoolean()
                    else -> false
                }
            }
        }
        
        return RenderedField(
            descriptor = descriptor,
            component = checkBox,
            getValue = { checkBox.isSelected },
            setValue = { value -> 
                checkBox.isSelected = when (value) {
                    is Boolean -> value
                    is String -> value.toBoolean()
                    else -> false
                }
            }
        )
    }
}

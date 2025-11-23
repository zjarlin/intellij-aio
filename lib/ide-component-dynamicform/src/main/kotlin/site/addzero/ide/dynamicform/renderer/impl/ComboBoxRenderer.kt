package site.addzero.ide.dynamicform.renderer.impl

import com.intellij.openapi.ui.ComboBox
import site.addzero.ide.dynamicform.model.ComboBoxDescriptor
import site.addzero.ide.dynamicform.model.FormFieldDescriptor
import site.addzero.ide.dynamicform.model.RenderedField
import site.addzero.ide.dynamicform.renderer.FieldRenderer

class ComboBoxRenderer : FieldRenderer<ComboBoxDescriptor> {
    
    override fun support(descriptor: FormFieldDescriptor) = 
        descriptor is ComboBoxDescriptor
    
    override fun render(descriptor: ComboBoxDescriptor): RenderedField {
        val comboBox = ComboBox(descriptor.options.toTypedArray()).apply {
            descriptor.defaultValue?.toString()?.let { defaultVal ->
                descriptor.options.indexOf(defaultVal)
                    .takeIf { it >= 0 }
                    ?.let { selectedIndex = it }
            }
        }
        
        return RenderedField(
            descriptor = descriptor,
            component = comboBox,
            getValue = { comboBox.selectedItem?.toString() },
            setValue = { value -> 
                value?.toString()?.let { strValue ->
                    descriptor.options.indexOf(strValue)
                        .takeIf { it >= 0 }
                        ?.let { comboBox.selectedIndex = it }
                }
            }
        )
    }
}

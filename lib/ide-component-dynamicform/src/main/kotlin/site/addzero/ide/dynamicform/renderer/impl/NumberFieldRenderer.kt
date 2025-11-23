package site.addzero.ide.dynamicform.renderer.impl

import site.addzero.ide.dynamicform.model.FormFieldDescriptor
import site.addzero.ide.dynamicform.model.NumberFieldDescriptor
import site.addzero.ide.dynamicform.model.RenderedField
import site.addzero.ide.dynamicform.renderer.FieldRenderer
import javax.swing.JFormattedTextField
import javax.swing.text.NumberFormatter
import java.text.NumberFormat

class NumberFieldRenderer : FieldRenderer<NumberFieldDescriptor> {
    
    override fun support(descriptor: FormFieldDescriptor) = 
        descriptor is NumberFieldDescriptor
    
    override fun render(descriptor: NumberFieldDescriptor): RenderedField {
        val formatter = NumberFormatter(NumberFormat.getNumberInstance()).apply {
            valueClass = Double::class.java
            if (descriptor.min != Double.MIN_VALUE) {
                minimum = descriptor.min
            }
            if (descriptor.max != Double.MAX_VALUE) {
                maximum = descriptor.max
            }
            allowsInvalid = false
        }
        
        val numberField = JFormattedTextField(formatter).apply {
            columns = 20
            descriptor.defaultValue?.let { 
                value = when (it) {
                    is Number -> it.toDouble()
                    is String -> it.toDoubleOrNull()
                    else -> 0.0
                }
            }
        }
        
        return RenderedField(
            descriptor = descriptor,
            component = numberField,
            getValue = { numberField.value },
            setValue = { value -> 
                numberField.value = when (value) {
                    is Number -> value.toDouble()
                    is String -> value.toDoubleOrNull() ?: 0.0
                    else -> 0.0
                }
            }
        )
    }
}

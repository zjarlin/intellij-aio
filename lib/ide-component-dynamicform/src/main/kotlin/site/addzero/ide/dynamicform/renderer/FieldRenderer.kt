package site.addzero.ide.dynamicform.renderer

import site.addzero.ide.dynamicform.model.FormFieldDescriptor
import site.addzero.ide.dynamicform.model.RenderedField

interface FieldRenderer<T : FormFieldDescriptor> {
    fun support(descriptor: FormFieldDescriptor): Boolean
    fun render(descriptor: T): RenderedField
}

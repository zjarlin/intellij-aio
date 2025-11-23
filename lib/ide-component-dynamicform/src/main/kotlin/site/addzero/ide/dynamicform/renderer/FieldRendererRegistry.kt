package site.addzero.ide.dynamicform.renderer

import site.addzero.ide.dynamicform.model.FormFieldDescriptor
import site.addzero.ide.dynamicform.model.RenderedField
import site.addzero.ide.dynamicform.renderer.impl.*

class FieldRendererRegistry(
    private val renderers: List<FieldRenderer<*>> = defaultRenderers()
) {
    
    @Suppress("UNCHECKED_CAST")
    fun render(descriptor: FormFieldDescriptor): RenderedField {
        return renderers
            .firstOrNull { it.support(descriptor) }
            ?.let { (it as FieldRenderer<FormFieldDescriptor>).render(descriptor) }
            ?: throw IllegalArgumentException("No renderer found for descriptor: ${descriptor::class.simpleName}")
    }
    
    fun registerRenderer(renderer: FieldRenderer<*>) = 
        FieldRendererRegistry(renderers + renderer)
    
    companion object {
        fun defaultRenderers(): List<FieldRenderer<*>> = listOf(
            TextFieldRenderer(),
            TextAreaRenderer(),
            ComboBoxRenderer(),
            CheckBoxRenderer(),
            NumberFieldRenderer(),
            PasswordFieldRenderer()
        )
        
        private val instance = FieldRendererRegistry()
        
        fun getInstance() = instance
    }
}

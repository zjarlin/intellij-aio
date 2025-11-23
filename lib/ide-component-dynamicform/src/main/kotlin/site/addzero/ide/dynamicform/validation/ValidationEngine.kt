package site.addzero.ide.dynamicform.validation

import site.addzero.ide.dynamicform.model.FormFieldDescriptor

class ValidationEngine(
    private val validators: List<FieldValidator> = defaultValidators()
) {
    
    fun validate(descriptor: FormFieldDescriptor, value: Any?): String? {
        return validators
            .filter { it.support(descriptor) }
            .firstNotNullOfOrNull { it.validate(descriptor, value) }
    }
    
    fun registerValidator(validator: FieldValidator) =
        ValidationEngine(validators + validator)
    
    companion object {
        fun defaultValidators(): List<FieldValidator> = listOf(
            RequiredFieldValidator(),
            NumberRangeValidator()
        )
    }
}

interface FieldValidator {
    fun support(descriptor: FormFieldDescriptor): Boolean
    fun validate(descriptor: FormFieldDescriptor, value: Any?): String?
}

class RequiredFieldValidator : FieldValidator {
    override fun support(descriptor: FormFieldDescriptor) = descriptor.required
    
    override fun validate(descriptor: FormFieldDescriptor, value: Any?): String? {
        return when {
            value == null -> "字段 ${descriptor.label} 不能为空"
            value is String && value.isBlank() -> "字段 ${descriptor.label} 不能为空"
            else -> null
        }
    }
}

class NumberRangeValidator : FieldValidator {
    override fun support(descriptor: FormFieldDescriptor) = 
        descriptor is site.addzero.ide.dynamicform.model.NumberFieldDescriptor
    
    override fun validate(descriptor: FormFieldDescriptor, value: Any?): String? {
        if (descriptor !is site.addzero.ide.dynamicform.model.NumberFieldDescriptor) return null
        
        val numValue = when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        } ?: return null
        
        return when {
            descriptor.min != Double.MIN_VALUE && numValue < descriptor.min ->
                "字段 ${descriptor.label} 不能小于 ${descriptor.min}"
            descriptor.max != Double.MAX_VALUE && numValue > descriptor.max ->
                "字段 ${descriptor.label} 不能大于 ${descriptor.max}"
            else -> null
        }
    }
}

package site.addzero.ide.dynamicform.parser

import site.addzero.ide.dynamicform.annotation.*
import site.addzero.ide.dynamicform.model.*
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField

class FormDescriptorParser {
    
    fun <T : Any> parse(dataClass: KClass<T>): FormDescriptor {
        val formConfig = dataClass.findAnnotation<FormConfig>()
        val formGroups = dataClass.findAnnotation<FormGroups>()
        
        val fieldDescriptors = dataClass.memberProperties
            .mapNotNull { property ->
                property.isAccessible = true
                parseFieldDescriptor(property.name, property.javaField?.annotations ?: emptyArray())
            }
            .sortedBy { it.order }
        
        val groupsMap = fieldDescriptors.groupBy { it.group }
        
        val groups = formGroups?.groups
            ?.sortedBy { it.order }
            ?.map { group ->
                FormGroupDescriptor(
                    name = group.name,
                    title = group.title,
                    order = group.order,
                    collapsible = group.collapsible,
                    fields = groupsMap[group.name] ?: emptyList()
                )
            }
            ?: listOf(
                FormGroupDescriptor(
                    name = "",
                    title = "",
                    order = 0,
                    collapsible = false,
                    fields = fieldDescriptors
                )
            )
        
        return FormDescriptor(
            title = formConfig?.title ?: dataClass.simpleName ?: "",
            description = formConfig?.description ?: "",
            groups = groups
        )
    }
    
    private fun parseFieldDescriptor(
        fieldName: String,
        annotations: Array<Annotation>
    ): FormFieldDescriptor? {
        var dependsOn: String? = null
        var visibilityPredicate: KClass<*>? = null
        
        annotations.find { it is DependentField }
            ?.let { it as DependentField }
            ?.also { 
                dependsOn = it.dependsOn
                visibilityPredicate = it.visibleWhen
            }
        
        return annotations.firstNotNullOfOrNull { annotation ->
            when (annotation) {
                is TextField -> TextFieldDescriptor(
                    name = fieldName,
                    label = annotation.label.ifEmpty { fieldName },
                    description = annotation.description,
                    group = annotation.group,
                    order = annotation.order,
                    required = annotation.required,
                    defaultValue = null,
                    dependsOn = dependsOn,
                    visibilityPredicate = visibilityPredicate,
                    placeholder = annotation.placeholder,
                    maxLength = annotation.maxLength
                )
                
                is TextArea -> TextAreaDescriptor(
                    name = fieldName,
                    label = annotation.label.ifEmpty { fieldName },
                    description = annotation.description,
                    group = annotation.group,
                    order = annotation.order,
                    required = annotation.required,
                    defaultValue = null,
                    dependsOn = dependsOn,
                    visibilityPredicate = visibilityPredicate,
                    placeholder = annotation.placeholder,
                    rows = annotation.rows,
                    maxLength = annotation.maxLength
                )
                
                is ComboBox -> {
                    val options = if (annotation.optionsProvider != EmptyOptionsProvider::class) {
                        annotation.optionsProvider.createInstance().getOptions()
                    } else {
                        annotation.options.toList()
                    }
                    
                    ComboBoxDescriptor(
                        name = fieldName,
                        label = annotation.label.ifEmpty { fieldName },
                        description = annotation.description,
                        group = annotation.group,
                        order = annotation.order,
                        required = annotation.required,
                        defaultValue = null,
                        dependsOn = dependsOn,
                        visibilityPredicate = visibilityPredicate,
                        options = options
                    )
                }
                
                is CheckBox -> CheckBoxDescriptor(
                    name = fieldName,
                    label = annotation.label.ifEmpty { fieldName },
                    description = annotation.description,
                    group = annotation.group,
                    order = annotation.order,
                    required = false,
                    defaultValue = null,
                    dependsOn = dependsOn,
                    visibilityPredicate = visibilityPredicate
                )
                
                is NumberField -> NumberFieldDescriptor(
                    name = fieldName,
                    label = annotation.label.ifEmpty { fieldName },
                    description = annotation.description,
                    group = annotation.group,
                    order = annotation.order,
                    required = annotation.required,
                    defaultValue = null,
                    dependsOn = dependsOn,
                    visibilityPredicate = visibilityPredicate,
                    min = annotation.min,
                    max = annotation.max
                )
                
                is PasswordField -> PasswordFieldDescriptor(
                    name = fieldName,
                    label = annotation.label.ifEmpty { fieldName },
                    description = annotation.description,
                    group = annotation.group,
                    order = annotation.order,
                    required = annotation.required,
                    defaultValue = null,
                    dependsOn = dependsOn,
                    visibilityPredicate = visibilityPredicate
                )
                
                else -> null
            }
        }
    }
}

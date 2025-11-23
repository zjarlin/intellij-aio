package site.addzero.ide.dynamicform.model

import javax.swing.JComponent
import kotlin.reflect.KClass

data class FormDescriptor(
    val title: String,
    val description: String,
    val groups: List<FormGroupDescriptor>
)

data class FormGroupDescriptor(
    val name: String,
    val title: String,
    val order: Int,
    val collapsible: Boolean,
    val fields: List<FormFieldDescriptor>
)

sealed class FormFieldDescriptor {
    abstract val name: String
    abstract val label: String
    abstract val description: String
    abstract val group: String
    abstract val order: Int
    abstract val required: Boolean
    abstract val defaultValue: Any?
    abstract val dependsOn: String?
    abstract val visibilityPredicate: KClass<*>?
}

data class TextFieldDescriptor(
    override val name: String,
    override val label: String,
    override val description: String,
    override val group: String,
    override val order: Int,
    override val required: Boolean,
    override val defaultValue: Any?,
    override val dependsOn: String?,
    override val visibilityPredicate: KClass<*>?,
    val placeholder: String,
    val maxLength: Int
) : FormFieldDescriptor()

data class TextAreaDescriptor(
    override val name: String,
    override val label: String,
    override val description: String,
    override val group: String,
    override val order: Int,
    override val required: Boolean,
    override val defaultValue: Any?,
    override val dependsOn: String?,
    override val visibilityPredicate: KClass<*>?,
    val placeholder: String,
    val rows: Int,
    val maxLength: Int
) : FormFieldDescriptor()

data class ComboBoxDescriptor(
    override val name: String,
    override val label: String,
    override val description: String,
    override val group: String,
    override val order: Int,
    override val required: Boolean,
    override val defaultValue: Any?,
    override val dependsOn: String?,
    override val visibilityPredicate: KClass<*>?,
    val options: List<String>
) : FormFieldDescriptor()

data class CheckBoxDescriptor(
    override val name: String,
    override val label: String,
    override val description: String,
    override val group: String,
    override val order: Int,
    override val required: Boolean = false,
    override val defaultValue: Any?,
    override val dependsOn: String?,
    override val visibilityPredicate: KClass<*>?
) : FormFieldDescriptor()

data class NumberFieldDescriptor(
    override val name: String,
    override val label: String,
    override val description: String,
    override val group: String,
    override val order: Int,
    override val required: Boolean,
    override val defaultValue: Any?,
    override val dependsOn: String?,
    override val visibilityPredicate: KClass<*>?,
    val min: Double,
    val max: Double
) : FormFieldDescriptor()

data class PasswordFieldDescriptor(
    override val name: String,
    override val label: String,
    override val description: String,
    override val group: String,
    override val order: Int,
    override val required: Boolean,
    override val defaultValue: Any?,
    override val dependsOn: String?,
    override val visibilityPredicate: KClass<*>?
) : FormFieldDescriptor()

data class RenderedField(
    val descriptor: FormFieldDescriptor,
    val component: JComponent,
    val getValue: () -> Any?,
    val setValue: (Any?) -> Unit
)

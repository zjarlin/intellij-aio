package site.addzero.ide.dynamicform.annotation

import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class FormConfig(
    val title: String = "",
    val description: String = ""
)

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class FormGroups(
    val groups: Array<FormGroup>
)

annotation class FormGroup(
    val name: String,
    val title: String,
    val order: Int = 0,
    val collapsible: Boolean = false
)

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class FormField(
    val label: String = "",
    val description: String = "",
    val group: String = "",
    val order: Int = 0,
    val required: Boolean = false,
    val placeholder: String = "",
    val renderer: KClass<out Any> = DefaultRenderer::class
)

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class TextField(
    val label: String = "",
    val description: String = "",
    val group: String = "",
    val order: Int = 0,
    val required: Boolean = false,
    val placeholder: String = "",
    val maxLength: Int = -1
)

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class TextArea(
    val label: String = "",
    val description: String = "",
    val group: String = "",
    val order: Int = 0,
    val required: Boolean = false,
    val placeholder: String = "",
    val rows: Int = 3,
    val maxLength: Int = -1
)

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class ComboBox(
    val label: String = "",
    val description: String = "",
    val group: String = "",
    val order: Int = 0,
    val required: Boolean = false,
    val options: Array<String> = [],
    val optionsProvider: KClass<out OptionsProvider> = EmptyOptionsProvider::class
)

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class CheckBox(
    val label: String = "",
    val description: String = "",
    val group: String = "",
    val order: Int = 0
)

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class NumberField(
    val label: String = "",
    val description: String = "",
    val group: String = "",
    val order: Int = 0,
    val required: Boolean = false,
    val min: Double = Double.MIN_VALUE,
    val max: Double = Double.MAX_VALUE
)

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class PasswordField(
    val label: String = "",
    val description: String = "",
    val group: String = "",
    val order: Int = 0,
    val required: Boolean = false
)

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class DependentField(
    val dependsOn: String,
    val visibleWhen: KClass<out VisibilityPredicate> = AlwaysVisible::class
)

interface OptionsProvider {
    fun getOptions(): List<String>
}

class EmptyOptionsProvider : OptionsProvider {
    override fun getOptions() = emptyList<String>()
}

interface VisibilityPredicate {
    fun isVisible(value: Any?): Boolean
}

class AlwaysVisible : VisibilityPredicate {
    override fun isVisible(value: Any?) = true
}

class DefaultRenderer

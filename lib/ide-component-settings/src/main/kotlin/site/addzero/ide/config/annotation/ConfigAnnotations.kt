package site.addzero.ide.config.annotation

import site.addzero.ide.config.model.InputType

/**
 * 用于标记配置类的注解
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Configurable

/**
 * 用于标记配置项的注解
 *
 * @param key 配置项的键，如果不指定则使用字段名
 * @param label 显示标签，如果不指定则使用字段名
 * @param description 配置项描述
 * @param required 是否必填
 * @param inputType 输入类型
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
annotation class ConfigField(
    val key: String = "",
    val label: String = "",
    val description: String = "",
    val required: Boolean = false,
    val inputType: InputType = InputType.TEXT
)

/**
 * 用于标记下拉框配置项的注解
 *
 * @param key 配置项的键，如果不指定则使用字段名
 * @param label 显示标签，如果不指定则使用字段名
 * @param description 配置项描述
 * @param optionsValue 下拉选项值数组
 * @param optionsLabel 下拉选项标签数组
 * @param required 是否必填
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
annotation class ConfigSelect(
    val key: String = "",
    val label: String = "",
    val description: String = "",
    val optionsValue: Array<String> = [],
    val optionsLabel: Array<String> = [],
    val required: Boolean = false
)

/**
 * 下拉选项注解
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.TYPE)
annotation class SelectOption(
    val value: String,
    val label: String
)

/**
 * 用于标记复选框配置项的注解
 *
 * @param key 配置项的键，如果不指定则使用字段名
 * @param label 显示标签，如果不指定则使用字段名
 * @param description 配置项描述
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
annotation class ConfigCheckbox(
    val key: String = "",
    val label: String = "",
    val description: String = ""
)

/**
 * 用于标记列表配置项的注解
 *
 * @param key 配置项的键，如果不指定则使用字段名
 * @param label 显示标签，如果不指定则使用字段名
 * @param description 配置项描述
 * @param maxItems 最大项数限制
 * @param minItems 最小项数限制
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class ConfigList(
    val key: String = "",
    val label: String = "",
    val description: String = "",
    val maxItems: Int = -1,
    val minItems: Int = -1
)

/**
 * 用于标记表格配置项的注解
 *
 * @param key 配置项的键，如果不指定则使用字段名
 * @param label 显示标签，如果不指定则使用字段名
 * @param description 配置项描述
 * @param maxRows 最大行数限制
 * @param minRows 最小行数限制
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
annotation class ConfigTable(
    val key: String = "",
    val label: String = "",
    val description: String = "",
    val maxRows: Int = -1,
    val minRows: Int = -1
)

/**
 * 用于标记条件配置项的注解
 *
 * @param key 配置项的键，如果不指定则使用字段名
 * @param label 显示标签，如果不指定则使用字段名
 * @param description 配置项描述
 * @param conditionField 依赖的字段
 * @param conditionOperator 条件操作符
 * @param conditionValue 比较值
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class ConfigConditional(
    val key: String = "",
    val label: String = "",
    val description: String = "",
    val conditionField: String,
    val conditionOperator: String,
    val conditionValue: String
)

/**
 * 用于定义配置路由的注解
 *
 * @param parent 父级菜单名称
 * @param name 当前菜单名称
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class SettingRoute(
    val parent: String,
    val path: Array<String> = []
)

package com.addzero.addl.settings

import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigField(
    val label: String,
    val type: FieldType = FieldType.TEXT,
    val options: Array<String> = [],
    val multiline: Boolean = false,
    val dependsOn: String = "", // 依赖字段名
    val predicateClass: KClass<out FieldDependencyPredicate> = DefaultDependencyPredicate::class, // 用于计算的谓词类
    val group: String = "", // 分组名称
    val order: Int = 0 // 在组内的排序
)

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class SettingsGroup(
    val groups: Array<Group>
)

annotation class Group(
    val name: String,
    val title: String,
    val order: Int
)

enum class FieldType {
    TEXT,       // 普通文本框
    DROPDOWN,   // 下拉框
    LONG_TEXT,  // 多行文本框
    SEPARATOR   // 分割线
}

object SettingContext {
    val settings = MyPluginSettingsService.getInstance().state
}

interface FieldDependencyPredicate {
    fun getOptions(dependentValue: Any?): Array<String>
}

class DefaultDependencyPredicate : FieldDependencyPredicate {
    override fun getOptions(dependentValue: Any?): Array<String> = arrayOf()
}
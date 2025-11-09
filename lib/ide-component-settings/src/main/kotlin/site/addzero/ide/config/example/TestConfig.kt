package site.addzero.ide.config.example

import site.addzero.ide.config.annotation.*

@SettingRoute("测试配置")
@Configurable
data class TestConfig(
    @ConfigField(
        label = "测试字段",
        description = "这是一个测试字段"
    )
    val testField: String = "默认值",

    @ConfigCheckbox(
        label = "启用测试",
        description = "是否启用测试功能"
    )
    val enableTest: Boolean = false
)

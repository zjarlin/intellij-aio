# IDE Component Settings

基于动态表单的IntelliJ插件设置模块。

## 概述

这是一个简化的设置模块，完全基于 `ide-component-dynamicform` 构建，提供：
- 类型安全的设置管理
- 自动持久化
- 零UI代码

## 快速开始

### 1. 定义设置数据类

```kotlin
import site.addzero.ide.dynamicform.annotation.*

@FormConfig(title = "我的设置")
@FormGroups(groups = [
    FormGroup(name = "basic", title = "基础", order = 1)
])
data class MySettings(
    @TextField(label = "名称", group = "basic", required = true)
    @JvmField var name: String = "",
    
    @ComboBox(label = "类型", group = "basic", options = ["A", "B"])
    @JvmField var type: String = "A"
)
```

### 2. 创建设置服务

```kotlin
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import site.addzero.ide.settings.ApplicationSettingsService

@State(
    name = "MySettings",
    storages = [Storage("MySettings.xml")]
)
class MySettingsService : ApplicationSettingsService<MySettings>(
    defaultSettings = MySettings(),
    dataClass = MySettings::class
) {
    companion object {
        fun getInstance(): MySettingsService =
            com.intellij.openapi.application.ApplicationManager
                .getApplication()
                .getService(MySettingsService::class.java)
    }
}
```

### 3. 创建Configurable

```kotlin
import site.addzero.ide.settings.createDynamicConfigurable

class MyConfigurable : com.intellij.openapi.options.Configurable by createDynamicConfigurable(
    displayName = "我的设置",
    settingsProvider = { MySettingsService.getInstance().getSettings() },
    onApply = { data -> MySettingsService.getInstance().updateSettings(data) }
)
```

### 4. 注册到plugin.xml

```xml
<extensions defaultExtensionNs="com.intellij">
    <applicationConfigurable 
        instance="com.example.MyConfigurable"
        id="my.settings"
        displayName="我的设置"/>
    
    <applicationService
        serviceImplementation="com.example.MySettingsService"/>
</extensions>
```

## 核心组件

### DynamicConfigurable

动态表单配置基类，自动处理：
- UI生成
- 数据绑定
- 修改检测
- 资源释放

```kotlin
abstract class DynamicConfigurable<T : Any>(
    displayName: String,
    dataClass: KClass<T>,
    project: Project? = null,
    settingsProvider: () -> T,
    onApply: (Map<String, Any?>) -> Unit
) : Configurable, Disposable
```

### SettingsService

设置持久化基类，支持：
- 自动序列化/反序列化
- 类型安全
- 应用级/项目级设置

```kotlin
abstract class SettingsService<T : Any>(
    defaultSettings: T,
    dataClass: KClass<T>
) : PersistentStateComponent<Map<String, String>>
```

## 完整示例

参见 `example/ExampleSettings.kt`，展示了：
- 所有字段类型的使用
- 分组和排序
- 验证和描述
- 占位符和提示

## 与旧系统的区别

### 旧系统
- 需要手写Swing UI代码（~250行）
- 手动数据绑定
- 手动修改检测
- 难以维护

### 新系统
- 只需注解定义（~50行）
- 自动数据绑定
- 自动修改检测
- 易于维护

## 依赖

```kotlin
dependencies {
    implementation(project(":lib:ide-component-dynamicform"))
}
```

## 相关文档

- [动态表单文档](../ide-component-dynamicform/README.md)
- [快速参考](../ide-component-dynamicform/QUICK_REFERENCE.md)
- [架构设计](../ide-component-dynamicform/ARCHITECTURE.md)

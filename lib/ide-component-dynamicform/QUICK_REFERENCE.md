# 快速参考指南

## 5分钟快速开始

### 1. 添加依赖
```kotlin
// build.gradle.kts
dependencies {
    implementation(project(":lib:ide-component-dynamicform"))
}
```

### 2. 定义配置类
```kotlin
import site.addzero.ide.dynamicform.annotation.*

@FormConfig(title = "我的设置")
@FormGroups(groups = [
    FormGroup(name = "basic", title = "基础", order = 1)
])
data class MySettings(
    @TextField(label = "名称", group = "basic", order = 1, required = true)
    var name: String = "",
    
    @ComboBox(label = "类型", group = "basic", order = 2, options = ["A", "B"])
    var type: String = "A"
)
```

### 3. 创建Configurable
```kotlin
import com.intellij.openapi.options.Configurable
import site.addzero.ide.dynamicform.engine.DynamicFormEngine
import javax.swing.JComponent

class MyConfigurable : Configurable {
    private val formEngine = DynamicFormEngine()
    
    override fun createComponent(): JComponent =
        formEngine.buildForm(MySettings::class, MySettings())
    
    override fun isModified() = formEngine.isModified()
    
    override fun apply() {
        val data = formEngine.getFormData()
        // 保存data到配置服务
        formEngine.reset()
    }
    
    override fun getDisplayName() = "我的设置"
}
```

## 注解快速参考

### 类注解

```kotlin
@FormConfig(
    title: String = "",           // 表单标题
    description: String = ""      // 表单描述
)

@FormGroups(
    groups: Array<FormGroup>      // 字段分组
)

FormGroup(
    name: String,                 // 分组名称
    title: String,                // 分组标题
    order: Int = 0,              // 显示顺序
    collapsible: Boolean = false  // 是否可折叠
)
```

### 字段注解

#### TextField - 单行文本
```kotlin
@TextField(
    label: String = "",          // 标签文本
    description: String = "",    // 描述文本
    group: String = "",          // 所属分组
    order: Int = 0,             // 显示顺序
    required: Boolean = false,   // 是否必填
    placeholder: String = "",    // 占位符
    maxLength: Int = -1         // 最大长度（-1表示无限制）
)
var field: String = ""
```

#### TextArea - 多行文本
```kotlin
@TextArea(
    label: String = "",
    description: String = "",
    group: String = "",
    order: Int = 0,
    required: Boolean = false,
    placeholder: String = "",
    rows: Int = 3,              // 显示行数
    maxLength: Int = -1
)
var field: String = ""
```

#### ComboBox - 下拉框
```kotlin
@ComboBox(
    label: String = "",
    description: String = "",
    group: String = "",
    order: Int = 0,
    required: Boolean = false,
    options: Array<String> = [],                           // 静态选项
    optionsProvider: KClass<out OptionsProvider> = ...    // 动态选项提供器
)
var field: String = ""
```

#### CheckBox - 复选框
```kotlin
@CheckBox(
    label: String = "",
    description: String = "",
    group: String = "",
    order: Int = 0
)
var field: Boolean = false
```

#### NumberField - 数字输入
```kotlin
@NumberField(
    label: String = "",
    description: String = "",
    group: String = "",
    order: Int = 0,
    required: Boolean = false,
    min: Double = Double.MIN_VALUE,  // 最小值
    max: Double = Double.MAX_VALUE   // 最大值
)
var field: Int = 0  // 或 Double, Long
```

#### PasswordField - 密码输入
```kotlin
@PasswordField(
    label: String = "",
    description: String = "",
    group: String = "",
    order: Int = 0,
    required: Boolean = false
)
var field: String = ""
```

## DynamicFormEngine API

### 创建表单
```kotlin
val engine = DynamicFormEngine()
val panel = engine.buildForm(MySettings::class, instanceOrNull)
```

### 获取表单数据
```kotlin
val data: Map<String, Any?> = engine.getFormData()
// 例如: {"name": "张三", "age": 25}
```

### 设置表单数据
```kotlin
val data = mapOf("name" to "张三", "age" to 25)
engine.setFormData(data)
```

### 检查是否修改
```kotlin
if (engine.isModified()) {
    // 用户修改了表单
}
```

### 验证表单
```kotlin
val result = engine.validate()
if (!result.isValid) {
    result.errors.forEach { (field, error) ->
        println("$field: $error")
    }
}
```

### 重置修改标记
```kotlin
engine.reset()
```

## 高级用法

### 动态选项提供器
```kotlin
class MyOptionsProvider : OptionsProvider {
    override fun getOptions(): List<String> {
        return fetchFromDatabase() // 从数据库、API等获取
    }
}

@ComboBox(
    label = "选项",
    optionsProvider = MyOptionsProvider::class
)
var option: String = ""
```

### 字段依赖
```kotlin
@ComboBox(label = "类型", options = ["A", "B"])
var type: String = "A"

@DependentField(
    dependsOn = "type",
    visibleWhen = TypeAPredicate::class
)
@TextField(label = "A的配置")
var configA: String = ""

class TypeAPredicate : VisibilityPredicate {
    override fun isVisible(value: Any?) = value == "A"
}
```

### 自定义渲染器
```kotlin
class MyRenderer : FieldRenderer<MyDescriptor> {
    override fun support(descriptor: FormFieldDescriptor) = 
        descriptor is MyDescriptor
    
    override fun render(descriptor: MyDescriptor): RenderedField {
        val component = JMyComponent()
        return RenderedField(
            descriptor = descriptor,
            component = component,
            getValue = { component.value },
            setValue = { component.value = it }
        )
    }
}

// 使用
val engine = DynamicFormEngine(
    rendererRegistry = FieldRendererRegistry.getInstance()
        .registerRenderer(MyRenderer())
)
```

### 自定义验证器
```kotlin
class EmailValidator : FieldValidator {
    override fun support(descriptor: FormFieldDescriptor) = 
        descriptor.name.contains("email")
    
    override fun validate(descriptor: FormFieldDescriptor, value: Any?): String? {
        val email = value?.toString() ?: return "不能为空"
        return if (isValidEmail(email)) null else "邮箱格式错误"
    }
}

// 使用
val engine = DynamicFormEngine(
    validationEngine = ValidationEngine(
        ValidationEngine.defaultValidators() + EmailValidator()
    )
)
```

## 常见模式

### 模式1: 简单配置
```kotlin
@FormConfig(title = "设置")
data class Settings(
    @TextField(label = "名称")
    var name: String = ""
)
```

### 模式2: 分组配置
```kotlin
@FormConfig(title = "设置")
@FormGroups(groups = [
    FormGroup(name = "group1", title = "分组1", order = 1),
    FormGroup(name = "group2", title = "分组2", order = 2)
])
data class Settings(
    @TextField(label = "字段1", group = "group1")
    var field1: String = "",
    
    @TextField(label = "字段2", group = "group2")
    var field2: String = ""
)
```

### 模式3: 带验证的配置
```kotlin
data class Settings(
    @TextField(label = "用户名", required = true)
    var username: String = "",
    
    @NumberField(label = "年龄", min = 0.0, max = 150.0)
    var age: Int = 0,
    
    @PasswordField(label = "密码", required = true)
    var password: String = ""
)
```

### 模式4: 复杂配置
```kotlin
@FormConfig(title = "高级设置", description = "详细配置说明")
@FormGroups(groups = [
    FormGroup(name = "basic", title = "基础配置", order = 1),
    FormGroup(name = "advanced", title = "高级配置", order = 2)
])
data class Settings(
    @TextField(
        label = "应用名称",
        group = "basic",
        order = 1,
        required = true,
        placeholder = "请输入应用名称"
    )
    var appName: String = "",
    
    @ComboBox(
        label = "日志级别",
        group = "basic",
        order = 2,
        options = ["DEBUG", "INFO", "WARN", "ERROR"]
    )
    var logLevel: String = "INFO",
    
    @NumberField(
        label = "端口",
        group = "advanced",
        order = 1,
        min = 1024.0,
        max = 65535.0
    )
    var port: Int = 8080,
    
    @TextArea(
        label = "备注",
        group = "advanced",
        order = 2,
        rows = 5
    )
    var notes: String = ""
)
```

## 故障排除

### 问题: 字段不显示
**解决**: 检查是否添加了`@JvmField`注解（如果使用Kotlin）

```kotlin
// ❌ 错误
@TextField(label = "名称")
var name: String = ""

// ✅ 正确
@TextField(label = "名称")
@JvmField var name: String = ""
```

### 问题: 验证不生效
**解决**: 确保设置了`required = true`

```kotlin
@TextField(label = "名称", required = true)
@JvmField var name: String = ""
```

### 问题: 分组不显示
**解决**: 检查字段的`group`属性是否与`@FormGroups`中定义的名称匹配

```kotlin
@FormGroups(groups = [
    FormGroup(name = "basic", title = "基础")  // 注意name
])
data class Settings(
    @TextField(label = "名称", group = "basic")  // group必须匹配
    var name: String = ""
)
```

### 问题: 选项不显示
**解决**: 确保`options`数组不为空

```kotlin
@ComboBox(
    label = "类型",
    options = ["选项1", "选项2"]  // 必须提供选项
)
@JvmField var type: String = ""
```

## 最佳实践

1. **使用分组** - 当字段超过5个时，使用分组
2. **设置顺序** - 使用`order`属性明确指定字段顺序
3. **提供描述** - 为复杂字段提供`description`
4. **合理使用必填** - 只标记真正必须的字段为`required`
5. **占位符提示** - 使用`placeholder`提供输入示例
6. **数值范围** - 为`NumberField`设置合理的`min`和`max`
7. **数据绑定** - 在`apply()`中保存数据到配置服务

## 性能提示

- ✅ 表单只在`createComponent()`时创建一次
- ✅ 使用懒加载，只在需要时创建组件
- ✅ 避免在循环中调用`buildForm()`
- ✅ 大型表单考虑使用分页或分组折叠

## 相关文档

- [README.md](README.md) - 完整文档
- [USAGE_EXAMPLE.md](USAGE_EXAMPLE.md) - 迁移示例
- [ARCHITECTURE.md](ARCHITECTURE.md) - 架构设计
- [SUMMARY.md](SUMMARY.md) - 项目总结

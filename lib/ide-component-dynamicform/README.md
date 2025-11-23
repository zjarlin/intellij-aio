# IDE Component Dynamic Form

基于Swing的动态表单库，通过注解定义data class即可自动生成表单UI。

## 特性

- 🎨 **注解驱动**: 只需使用注解标注data class即可生成表单
- 🔧 **策略模式**: 使用渲染器策略模式，易于扩展新的字段类型
- ✅ **内置验证**: 支持必填、范围等验证规则
- 📦 **分组支持**: 支持字段分组和排序
- 🔄 **依赖字段**: 支持字段间的依赖关系
- 🎯 **类型安全**: 完全类型安全的Kotlin实现

## 快速开始

### 1. 定义数据类

```kotlin
@FormConfig(
    title = "应用设置",
    description = "配置应用的各项参数"
)
@FormGroups(
    groups = [
        FormGroup(name = "basic", title = "基础设置", order = 1),
        FormGroup(name = "advanced", title = "高级设置", order = 2)
    ]
)
data class AppSettings(
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
        label = "端口号",
        group = "advanced",
        order = 1,
        required = true,
        min = 1024.0,
        max = 65535.0
    )
    var port: Int = 8080,
    
    @CheckBox(
        label = "启用SSL",
        group = "advanced",
        order = 2
    )
    var enableSsl: Boolean = false,
    
    @PasswordField(
        label = "管理员密码",
        group = "advanced",
        order = 3,
        required = true
    )
    var adminPassword: String = "",
    
    @TextArea(
        label = "备注",
        group = "advanced",
        order = 4,
        rows = 3
    )
    var notes: String = ""
)
```

### 2. 生成表单

```kotlin
val formEngine = DynamicFormEngine()
val settings = AppSettings()

// 构建表单
val formPanel = formEngine.buildForm(AppSettings::class, settings)

// 添加到UI
parentPanel.add(formPanel)
```

### 3. 获取和设置数据

```kotlin
// 获取表单数据
val formData: Map<String, Any?> = formEngine.getFormData()

// 设置表单数据
val data = mapOf(
    "appName" to "MyApp",
    "logLevel" to "DEBUG",
    "port" to 8080,
    "enableSsl" to true
)
formEngine.setFormData(data)

// 检查是否修改
if (formEngine.isModified()) {
    // 处理修改
}

// 验证表单
val result = formEngine.validate()
if (!result.isValid) {
    result.errors.forEach { (field, error) ->
        println("$field: $error")
    }
}
```

## 支持的字段类型

### TextField - 文本输入框
```kotlin
@TextField(
    label = "用户名",
    placeholder = "请输入用户名",
    maxLength = 50,
    required = true
)
var username: String = ""
```

### TextArea - 多行文本框
```kotlin
@TextArea(
    label = "描述",
    rows = 5,
    maxLength = 500
)
var description: String = ""
```

### ComboBox - 下拉选择框
```kotlin
@ComboBox(
    label = "角色",
    options = ["Admin", "User", "Guest"]
)
var role: String = "User"

// 或使用OptionsProvider动态提供选项
@ComboBox(
    label = "数据库",
    optionsProvider = DatabaseOptionsProvider::class
)
var database: String = ""

class DatabaseOptionsProvider : OptionsProvider {
    override fun getOptions() = listOf("MySQL", "PostgreSQL", "Oracle")
}
```

### CheckBox - 复选框
```kotlin
@CheckBox(label = "记住我")
var rememberMe: Boolean = false
```

### NumberField - 数字输入框
```kotlin
@NumberField(
    label = "年龄",
    min = 0.0,
    max = 150.0,
    required = true
)
var age: Int = 0
```

### PasswordField - 密码输入框
```kotlin
@PasswordField(
    label = "密码",
    required = true
)
var password: String = ""
```

## 高级特性

### 字段依赖

```kotlin
@ComboBox(
    label = "数据库类型",
    options = ["MySQL", "PostgreSQL"]
)
var dbType: String = "MySQL"

@DependentField(
    dependsOn = "dbType",
    visibleWhen = MySQLPredicate::class
)
@TextField(label = "MySQL端口")
var mysqlPort: String = "3306"

class MySQLPredicate : VisibilityPredicate {
    override fun isVisible(value: Any?) = value == "MySQL"
}
```

### 自定义渲染器

```kotlin
class CustomFieldRenderer : FieldRenderer<CustomFieldDescriptor> {
    override fun support(descriptor: FormFieldDescriptor) = 
        descriptor is CustomFieldDescriptor
    
    override fun render(descriptor: CustomFieldDescriptor): RenderedField {
        val component = JCustomComponent()
        return RenderedField(
            descriptor = descriptor,
            component = component,
            getValue = { component.getValue() },
            setValue = { value -> component.setValue(value) }
        )
    }
}

// 注册自定义渲染器
val registry = FieldRendererRegistry.getInstance()
    .registerRenderer(CustomFieldRenderer())

val formEngine = DynamicFormEngine(
    rendererRegistry = registry
)
```

### 自定义验证器

```kotlin
class EmailValidator : FieldValidator {
    override fun support(descriptor: FormFieldDescriptor) = 
        descriptor.name == "email"
    
    override fun validate(descriptor: FormFieldDescriptor, value: Any?): String? {
        val email = value?.toString() ?: return "邮箱不能为空"
        return if (email.matches(Regex("^[A-Za-z0-9+_.-]+@(.+)$"))) {
            null
        } else {
            "邮箱格式不正确"
        }
    }
}

// 注册自定义验证器
val validationEngine = ValidationEngine.defaultValidators()
    .plus(EmailValidator())
    .let { ValidationEngine(it) }

val formEngine = DynamicFormEngine(
    validationEngine = validationEngine
)
```

## 在IntelliJ插件中使用

```kotlin
class MyPluginConfigurable : Configurable {
    private val formEngine = DynamicFormEngine()
    private var formPanel: JPanel? = null
    
    override fun createComponent(): JComponent {
        val settings = MyPluginSettings()
        formPanel = formEngine.buildForm(MyPluginSettings::class, settings)
        return formPanel!!
    }
    
    override fun isModified() = formEngine.isModified()
    
    override fun apply() {
        val formData = formEngine.getFormData()
        // 保存设置
        MySettingsService.getInstance().saveSettings(formData)
        formEngine.reset()
    }
    
    override fun getDisplayName() = "My Plugin Settings"
}
```

## 架构设计

### 策略模式

每种字段类型都有对应的渲染器实现`FieldRenderer`接口：

```
FieldRenderer (interface)
├── TextFieldRenderer
├── TextAreaRenderer
├── ComboBoxRenderer
├── CheckBoxRenderer
├── NumberFieldRenderer
└── PasswordFieldRenderer
```

通过`FieldRendererRegistry`统一管理，使用`support()`方法进行策略选择。

### 注解到描述符的转换

1. `FormDescriptorParser`解析注解
2. 生成`FormFieldDescriptor`描述对象
3. `FieldRendererRegistry`选择合适的渲染器
4. 渲染器生成`RenderedField`（包含组件和getter/setter）
5. `DynamicFormEngine`组装最终的表单面板

## License

MIT License

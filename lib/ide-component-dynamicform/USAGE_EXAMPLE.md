# 动态表单使用示例

## 完整的迁移示例

### 旧的方式 (ide-component-settings-old)

之前需要手动编写大量Swing代码：

```kotlin
class MyPluginConfigurable : Configurable, Disposable {
    private var settings = MyPluginSettingsService.getInstance().state
    private lateinit var panel: JPanel
    private val components = mutableMapOf<String, JComponent>()
    
    override fun createComponent(): JPanel {
        panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            gridx = 0
            insets = JBUI.insets(5)
        }
        
        // 手动创建每个字段...
        val label = JLabel("模型Key")
        val textField = JTextField(settings.modelKey)
        components["modelKey"] = textField
        
        gbc.gridx = 0
        panel.add(label, gbc)
        gbc.gridx = 1
        panel.add(textField, gbc)
        
        // 对每个字段重复上述过程... 几百行代码
        
        return panel
    }
    
    override fun isModified(): Boolean {
        // 手动检查每个字段...
        return components.any { (fieldName, component) ->
            val field = MyPluginSettings::class.java.getDeclaredField(fieldName)
            val currentValue = field.get(settings)
            val newValue = when (component) {
                is JTextField -> component.text
                is JComboBox<*> -> component.selectedItem
                else -> null
            }
            currentValue != newValue
        }
    }
    
    override fun apply() {
        // 手动应用每个字段的值...
    }
}
```

### 新的方式 (使用 ide-component-dynamicform)

只需要定义数据类和注解：

```kotlin
// 1. 定义配置数据类
@FormConfig(
    title = "AutoDDL设置",
    description = "配置AutoDDL插件的各项参数"
)
@FormGroups(
    groups = [
        FormGroup(name = "ai", title = "AI模型配置", order = 1),
        FormGroup(name = "db", title = "数据库配置", order = 2)
    ]
)
data class MyPluginSettings(
    @TextField(
        label = "模型Key",
        group = "ai",
        order = 1,
        required = true,
        placeholder = "请输入API密钥"
    )
    @JvmField var modelKey: String = "",
    
    @ComboBox(
        label = "模型厂商",
        group = "ai",
        order = 2,
        options = ["DashScope", "Ollama", "DeepSeek"]
    )
    @JvmField var modelManufacturer: String = "DeepSeek",
    
    @ComboBox(
        label = "数据库类型",
        group = "db",
        order = 1,
        options = ["mysql", "oracle", "pg", "dm", "h2"]
    )
    @JvmField var dbType: String = "mysql"
)

// 2. 创建Configurable（只需要几行代码）
class MyPluginConfigurable : Configurable, Disposable {
    private var settings = MyPluginSettingsService.getInstance().state
    private val formEngine = DynamicFormEngine()
    private var formPanel: JPanel? = null
    
    override fun createComponent(): JComponent {
        formPanel = formEngine.buildForm(MyPluginSettings::class, settings)
        return formPanel!!
    }
    
    override fun isModified() = formEngine.isModified()
    
    override fun apply() {
        val formData = formEngine.getFormData()
        formData.forEach { (fieldName, value) ->
            val field = MyPluginSettings::class.java.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(settings, value)
        }
        formEngine.reset()
    }
    
    override fun getDisplayName() = "AutoDDL设置"
    
    override fun dispose() {
        formPanel = null
    }
}
```

## 代码量对比

| 方式 | 行数 | 维护性 | 扩展性 |
|------|------|--------|--------|
| 旧方式 | ~250行 | 差（大量重复代码） | 差（添加字段需要修改多处） |
| 新方式 | ~50行 | 优（声明式） | 优（添加字段只需加注解） |

## 核心优势

### 1. 声明式编程
只需声明需要什么字段，框架自动生成UI：

```kotlin
@TextField(label = "用户名", required = true)
var username: String = ""
```

### 2. 策略模式架构
每种字段类型都有对应的渲染器：

```kotlin
interface FieldRenderer<T : FormFieldDescriptor> {
    fun support(descriptor: FormFieldDescriptor): Boolean
    fun render(descriptor: T): RenderedField
}
```

添加新字段类型只需实现新的渲染器，无需修改现有代码。

### 3. 函数式+Stream写法
符合你的代码风格偏好：

```kotlin
// 获取所有必填字段的验证错误
val errors = renderedFields
    .mapNotNull { (name, field) ->
        validationEngine.validate(field.descriptor, field.getValue())
            ?.let { name to it }
    }
    .toMap()

// 应用表单数据
formData.forEach { (name, value) ->
    renderedFields[name]?.setValue(value)
}
```

### 4. DI友好
使用构造函数注入依赖：

```kotlin
class DynamicFormEngine(
    private val parser: FormDescriptorParser = FormDescriptorParser(),
    private val rendererRegistry: FieldRendererRegistry = FieldRendererRegistry.getInstance(),
    private val validationEngine: ValidationEngine = ValidationEngine()
) {
    // ...
}
```

### 5. 策略组合
可以动态注册新的渲染器和验证器：

```kotlin
val customRegistry = FieldRendererRegistry.getInstance()
    .registerRenderer(CustomFieldRenderer())
    .registerRenderer(AnotherCustomRenderer())

val customValidation = ValidationEngine()
    .registerValidator(EmailValidator())
    .registerValidator(PhoneValidator())

val formEngine = DynamicFormEngine(
    rendererRegistry = customRegistry,
    validationEngine = customValidation
)
```

## 实际迁移步骤

### Step 1: 添加依赖
```kotlin
// build.gradle.kts
dependencies {
    implementation(project(":lib:ide-component-dynamicform"))
}
```

### Step 2: 迁移数据类
将旧的`@ConfigField`注解替换为新的注解：

```kotlin
// 旧注解
@ConfigField(
    label = "模型Key",
    type = FieldType.TEXT,
    group = "ai",
    order = 1
)
@JvmField var modelKey: String = ""

// 新注解
@TextField(
    label = "模型Key",
    group = "ai",
    order = 1,
    required = true,
    placeholder = "请输入API密钥"
)
@JvmField var modelKey: String = ""
```

### Step 3: 简化Configurable
删除手动创建UI的代码，使用`DynamicFormEngine`：

```kotlin
class MyPluginConfigurable : Configurable {
    private val formEngine = DynamicFormEngine()
    private var formPanel: JPanel? = null
    
    override fun createComponent() = 
        formEngine.buildForm(MyPluginSettings::class, getSettings())
            .also { formPanel = it }
    
    override fun isModified() = formEngine.isModified()
    
    override fun apply() {
        formEngine.getFormData().forEach { (name, value) ->
            // 保存到配置服务
        }
        formEngine.reset()
    }
    
    override fun getDisplayName() = "My Settings"
}
```

## 高级用法

### 自定义渲染器示例

```kotlin
// 创建一个颜色选择器渲染器
class ColorPickerRenderer : FieldRenderer<ColorFieldDescriptor> {
    override fun support(descriptor: FormFieldDescriptor) = 
        descriptor is ColorFieldDescriptor
    
    override fun render(descriptor: ColorFieldDescriptor): RenderedField {
        val colorChooser = JColorChooser()
        val button = JButton("选择颜色").apply {
            addActionListener {
                val color = JColorChooser.showDialog(
                    null, "选择颜色", colorChooser.color
                )
                if (color != null) {
                    background = color
                }
            }
        }
        
        return RenderedField(
            descriptor = descriptor,
            component = button,
            getValue = { button.background },
            setValue = { value -> 
                (value as? Color)?.let { button.background = it }
            }
        )
    }
}

// 注册使用
val formEngine = DynamicFormEngine(
    rendererRegistry = FieldRendererRegistry.getInstance()
        .registerRenderer(ColorPickerRenderer())
)
```

### 动态选项提供器

```kotlin
// 定义选项提供器
class DatabaseOptionsProvider : OptionsProvider {
    override fun getOptions(): List<String> {
        // 可以从配置、数据库或API获取选项
        return DatabaseConfig.getSupportedDatabases()
    }
}

// 使用
@ComboBox(
    label = "数据库类型",
    optionsProvider = DatabaseOptionsProvider::class
)
var database: String = ""
```

## 总结

使用动态表单系统后：
- ✅ 代码量减少80%
- ✅ 维护性大幅提升
- ✅ 添加新字段只需几行注解
- ✅ 符合函数式编程风格
- ✅ 使用策略模式，易于扩展
- ✅ 类型安全
- ✅ 自动验证和错误提示

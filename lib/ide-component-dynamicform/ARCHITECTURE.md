# 动态表单架构设计

## 设计理念

### 1. 策略模式 (Strategy Pattern)
每种字段类型都有对应的渲染器策略，通过`support()`方法决定是否处理该字段。

```
FieldRenderer (策略接口)
├── TextFieldRenderer (文本框策略)
├── TextAreaRenderer (文本域策略)
├── ComboBoxRenderer (下拉框策略)
├── CheckBoxRenderer (复选框策略)
├── NumberFieldRenderer (数字框策略)
└── PasswordFieldRenderer (密码框策略)
```

### 2. 依赖注入 (DI)
所有主要组件都支持构造函数注入：

```kotlin
class DynamicFormEngine(
    private val parser: FormDescriptorParser = FormDescriptorParser(),
    private val rendererRegistry: FieldRendererRegistry = FieldRendererRegistry.getInstance(),
    private val validationEngine: ValidationEngine = ValidationEngine()
)
```

### 3. 函数式编程
大量使用Stream流式操作：

```kotlin
val errors = renderedFields
    .mapNotNull { (name, field) ->
        validationEngine.validate(field.descriptor, field.getValue())
            ?.let { name to it }
    }
    .toMap()
```

### 4. 声明式UI
使用注解声明UI结构，而不是命令式构建：

```kotlin
@FormGroups(groups = [...])
data class Settings(
    @TextField(label = "...")
    var field: String = ""
)
```

## 核心组件

### 1. 注解层 (Annotation Layer)

```
FormAnnotations.kt
├── @FormConfig - 表单配置
├── @FormGroups - 分组定义
├── @TextField - 文本框
├── @TextArea - 文本域
├── @ComboBox - 下拉框
├── @CheckBox - 复选框
├── @NumberField - 数字框
├── @PasswordField - 密码框
└── @DependentField - 依赖字段
```

### 2. 模型层 (Model Layer)

```
FormModels.kt
├── FormDescriptor - 表单描述
├── FormGroupDescriptor - 分组描述
├── FormFieldDescriptor - 字段描述（sealed class）
│   ├── TextFieldDescriptor
│   ├── TextAreaDescriptor
│   ├── ComboBoxDescriptor
│   ├── CheckBoxDescriptor
│   ├── NumberFieldDescriptor
│   └── PasswordFieldDescriptor
└── RenderedField - 渲染结果
```

`FormFieldDescriptor`使用`sealed class`确保类型安全。

### 3. 解析器 (Parser)

```kotlin
class FormDescriptorParser {
    fun <T : Any> parse(dataClass: KClass<T>): FormDescriptor {
        // 1. 读取类级别注解 (@FormConfig, @FormGroups)
        // 2. 扫描字段注解
        // 3. 转换为 FormFieldDescriptor
        // 4. 按分组和顺序组织
        // 5. 返回 FormDescriptor
    }
}
```

### 4. 渲染器策略 (Renderer Strategy)

```kotlin
interface FieldRenderer<T : FormFieldDescriptor> {
    fun support(descriptor: FormFieldDescriptor): Boolean
    fun render(descriptor: T): RenderedField
}
```

每个渲染器负责：
- 判断是否支持该字段类型
- 创建对应的Swing组件
- 提供getValue和setValue闭包

### 5. 渲染器注册表 (Registry)

```kotlin
class FieldRendererRegistry(
    private val renderers: List<FieldRenderer<*>>
) {
    fun render(descriptor: FormFieldDescriptor): RenderedField {
        return renderers
            .firstOrNull { it.support(descriptor) }
            ?.let { (it as FieldRenderer<FormFieldDescriptor>).render(descriptor) }
            ?: throw IllegalArgumentException("No renderer found")
    }
    
    fun registerRenderer(renderer: FieldRenderer<*>) =
        FieldRendererRegistry(renderers + renderer)
}
```

使用不可变列表，`registerRenderer`返回新实例，符合函数式风格。

### 6. 验证引擎 (Validation Engine)

```kotlin
interface FieldValidator {
    fun support(descriptor: FormFieldDescriptor): Boolean
    fun validate(descriptor: FormFieldDescriptor, value: Any?): String?
}

class ValidationEngine(
    private val validators: List<FieldValidator>
) {
    fun validate(descriptor: FormFieldDescriptor, value: Any?): String? {
        return validators
            .filter { it.support(descriptor) }
            .firstNotNullOfOrNull { it.validate(descriptor, value) }
    }
}
```

内置验证器：
- `RequiredFieldValidator` - 必填验证
- `NumberRangeValidator` - 数值范围验证

### 7. 表单引擎 (Form Engine)

```kotlin
class DynamicFormEngine {
    // 构建表单
    fun <T : Any> buildForm(dataClass: KClass<T>, instance: T?): JPanel
    
    // 获取表单数据
    fun getFormData(): Map<String, Any?>
    
    // 设置表单数据
    fun setFormData(data: Map<String, Any?>)
    
    // 检查是否修改
    fun isModified(): Boolean
    
    // 验证表单
    fun validate(): ValidationResult
    
    // 重置修改标记
    fun reset()
}
```

## 数据流

```
1. 用户定义 Data Class + 注解
           ↓
2. FormDescriptorParser 解析
           ↓
3. 生成 FormDescriptor
           ↓
4. DynamicFormEngine 处理
           ↓
5. 对每个字段:
   - FieldRendererRegistry 选择渲染器
   - FieldRenderer 渲染组件
   - 生成 RenderedField
           ↓
6. 组装成 JPanel
           ↓
7. 显示在UI中
```

## 策略选择流程

```kotlin
renderers.firstOrNull { it.support(descriptor) }
```

### 示例：
1. `TextFieldRenderer.support(descriptor)` → true
2. 选择 `TextFieldRenderer`
3. 调用 `TextFieldRenderer.render(descriptor)`
4. 返回 `RenderedField`

## 扩展点

### 1. 自定义渲染器

```kotlin
class MyCustomRenderer : FieldRenderer<MyCustomDescriptor> {
    override fun support(descriptor: FormFieldDescriptor) = 
        descriptor is MyCustomDescriptor
    
    override fun render(descriptor: MyCustomDescriptor): RenderedField {
        // 创建自定义组件
    }
}

// 注册
val registry = FieldRendererRegistry.getInstance()
    .registerRenderer(MyCustomRenderer())
```

### 2. 自定义验证器

```kotlin
class EmailValidator : FieldValidator {
    override fun support(descriptor: FormFieldDescriptor) = 
        descriptor.name == "email"
    
    override fun validate(descriptor: FormFieldDescriptor, value: Any?): String? {
        // 验证逻辑
    }
}

// 注册
val engine = ValidationEngine()
    .registerValidator(EmailValidator())
```

### 3. 自定义选项提供器

```kotlin
class DynamicOptionsProvider : OptionsProvider {
    override fun getOptions(): List<String> {
        // 动态获取选项
        return ApiClient.fetchOptions()
    }
}

// 使用
@ComboBox(
    label = "选项",
    optionsProvider = DynamicOptionsProvider::class
)
var option: String = ""
```

## 设计模式总结

### 使用的设计模式：

1. **策略模式** - FieldRenderer系列
2. **工厂模式** - FormDescriptorParser
3. **注册表模式** - FieldRendererRegistry
4. **建造者模式** - DynamicFormEngine
5. **适配器模式** - RenderedField包装组件
6. **责任链模式** - ValidationEngine的验证器链
7. **模板方法模式** - FieldRenderer的render流程

### 符合的设计原则：

1. **开闭原则** - 对扩展开放（可添加新渲染器），对修改关闭
2. **单一职责** - 每个类只负责一件事
3. **依赖倒置** - 依赖抽象接口而非具体实现
4. **接口隔离** - 小而专注的接口
5. **里氏替换** - 所有渲染器可以互相替换

## 性能考虑

### 1. 懒加载
```kotlin
private var formPanel: JPanel? = null

override fun createComponent(): JComponent {
    if (formPanel == null) {
        formPanel = formEngine.buildForm(...)
    }
    return formPanel!!
}
```

### 2. 不可变集合
使用不可变列表避免并发问题：
```kotlin
fun registerRenderer(renderer: FieldRenderer<*>) =
    FieldRendererRegistry(renderers + renderer)
```

### 3. Stream短路
使用`firstOrNull`和`firstNotNullOfOrNull`进行短路求值：
```kotlin
renderers.firstOrNull { it.support(descriptor) }
```

## 测试策略

### 1. 单元测试
- 测试每个渲染器的`support()`和`render()`
- 测试每个验证器的`validate()`
- 测试解析器的`parse()`

### 2. 集成测试
- 测试完整的表单生成流程
- 测试数据绑定
- 测试验证流程

### 3. UI测试
- 在实际IntelliJ环境中测试
- 测试用户交互
- 测试修改检测

## 未来扩展

### 可能的增强：
1. 支持更多字段类型（日期选择器、文件选择器等）
2. 支持字段间的复杂依赖关系
3. 支持动态显示/隐藏字段
4. 支持字段分页
5. 支持国际化
6. 支持主题定制
7. 支持表单模板
8. 支持表单预览和导出

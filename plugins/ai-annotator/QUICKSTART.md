# AI Annotator - 5 分钟快速开始

## 第 1 步：安装插件 (1 分钟)

```bash
cd autoddl-idea-plugin
./gradlew :plugins:ai-annotator:buildPlugin
```

在 IDEA 中：`Settings` → `Plugins` → `Install Plugin from Disk` → 选择生成的 ZIP → 重启

## 第 2 步：配置 AI (2 分钟)

打开 `Settings` → `Tools` → `AI Annotator`

### 使用 DeepSeek (推荐)

1. 访问 https://platform.deepseek.com 注册并获取 API Key
2. 配置：
   - AI 提供商：DeepSeek
   - API Key：sk-your-api-key
   - 模型名称：deepseek-chat
   - API Base URL：https://api.deepseek.com
   - Temperature：0.3

### 或使用 Ollama (本地免费)

1. 安装 Ollama：https://ollama.com
2. 运行：`ollama pull qwen2.5-coder:7b`
3. 配置：
   - AI 提供商：Ollama
   - 模型名称：qwen2.5-coder:7b
   - API Base URL：http://localhost:11434

## 第 3 步：使用 (2 分钟)

### Java 示例

```java
public class User {
    private Long userId;
    private String userName;
    private Date createTime;
}
```

1. 光标放在类名上
2. 按 `Alt+Enter`
3. 选择 "Add Swagger Annotation"
4. 等待几秒，完成！

**结果**：
```java
public class User {
    @Schema(description = "用户ID")
    private Long userId;
    
    @Schema(description = "用户名称")
    private String userName;
    
    @Schema(description = "创建时间")
    private Date createTime;
}
```

### Kotlin 示例

```kotlin
data class Product(
    val productId: Long,
    val productName: String,
    val price: BigDecimal
)
```

同样按 `Alt+Enter` → "Add Swagger Annotation"

**结果**：
```kotlin
data class Product(
    @get:Schema(description = "产品ID")
    val productId: Long,
    
    @get:Schema(description = "产品名称")
    val productName: String,
    
    @get:Schema(description = "价格")
    val price: BigDecimal
)
```

## 可用的操作

- **Add Swagger Annotation** - 添加 @Schema / @ApiModelProperty
- **Add Excel Annotation** - 添加 @ExcelProperty / @Excel
- **Add Custom Annotation** - 添加自定义注解

## 高级配置

### 自定义注解模板

在 Settings 中修改模板，`{}` 会被替换为注释：

- Swagger：`@Schema(description = "{}")`
- Excel：`@ExcelProperty("{}")`
- 自定义：`@ApiModelProperty(value = "{}")`

### 功能开关

- **启用 AI 推测**：控制是否调用 AI
- **启用批量处理**：一次性处理所有字段（更快）

## 常见问题

### Q: 没有生成注释？
A: 检查 API Key 是否正确，查看 IDEA Event Log 错误信息

### Q: 生成的注释不准确？
A: 调整 Temperature 为 0.3，或使用更强大的模型

### Q: 费用如何？
A: DeepSeek 约 0.14元/百万 tokens，一个类（10个字段）约 0.001元

## 下一步

- 📖 阅读 [README.md](README.md) 了解详细功能
- ⚙️ 在项目中添加注解依赖（Swagger、Excel 等）
- 🤖 尝试不同的 AI 提供商找到最适合的

## 快速参考

| 操作 | 快捷键 | 说明 |
|------|--------|------|
| 打开上下文菜单 | `Alt+Enter` | 显示可用操作 |
| 配置插件 | `Settings → Tools → AI Annotator` | 修改配置 |

---

**开始使用吧！遇到问题？提交 Issue：https://gitee.com/zjarlin/autoddl-idea-plugin/issues**

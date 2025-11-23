

# AI Annotator Plugin

## 简介

AI Annotator 是一个智能的 IntelliJ IDEA 插件，能够自动为 Java/Kotlin 类的字段生成注解和注释。当字段没有注释时，插件会调用 AI API 根据字段名智能推测其含义。

## 核心功能

### 🤖 AI 智能推测
- 当字段没有注释时，自动调用 AI 根据字段名推测其语义
- 支持批量处理，一次性为多个字段生成注释
- 支持多种 AI 提供商（DeepSeek、OpenAI、Ollama、DashScope）

### 📝 多种注解支持
- **Swagger 注解**：`@Schema(description = "...")`、`@ApiModelProperty(value = "...")`
- **Excel 注解**：`@ExcelProperty("...")`、`@Excel("...")`
- **自定义注解**：可配置任意注解模板

### 🔄 智能注释来源
插件会按以下优先级获取字段注释：
1. Javadoc / KDoc 注释
2. 已有的 Swagger 注解
3. 已有的 Excel 注解  
4. 已有的其他注解
5. AI 推测（如果启用且配置了 API Key）

### 💻 双语言支持
- 完整支持 Java 和 Kotlin
- 自动识别文件类型并使用相应的 PSI API

## 安装

1. 从源码构建：
   ```bash
   cd autoddl-idea-plugin
   ./gradlew :plugins:ai-annotator:buildPlugin
   ```

2. 在 IDEA 中安装：
   - `Settings` → `Plugins` → `⚙️` → `Install Plugin from Disk`
   - 选择 `plugins/ai-annotator/build/distributions/ai-annotator-*.zip`
   - 重启 IDEA

## 配置

### 第 1 步：配置 AI 服务

打开 `Settings` → `Tools` → `AI Annotator`

#### AI 配置
- **AI 提供商**：选择您使用的 AI 服务
  - DeepSeek（推荐）：性价比高，专注代码理解
  - OpenAI：GPT 系列模型
  - Ollama：本地部署，无需 API Key
  - DashScope：阿里云通义千问

- **API Key**：输入您的 API 密钥
  - DeepSeek：在 https://platform.deepseek.com 获取
  - OpenAI：在 https://platform.openai.com 获取

- **模型名称**：
  - DeepSeek：`deepseek-chat` 或 `deepseek-coder`
  - OpenAI：`gpt-3.5-turbo` 或 `gpt-4`
  - Ollama：本地安装的模型名

- **API Base URL**：
  - DeepSeek：`https://api.deepseek.com`
  - OpenAI：`https://api.openai.com`
  - Ollama：`http://localhost:11434`

- **Temperature**：控制 AI 生成的随机性（0.0-1.0）
  - 推荐值：0.3（更确定性）

#### 注解模板配置

自定义注解的格式，`{}` 会被替换为字段注释：

- **Swagger 注解**：`@Schema(description = "{}")`
- **Excel 注解**：`@ExcelProperty("{}")`
- **自定义注解**：`@ApiModelProperty(value = "{}")`

#### 功能选项

- **启用 AI 推测字段注释**：当字段无注释时是否调用 AI
- **启用批量处理**：是否一次性为所有字段调用 AI（更高效）

### 第 2 步：在项目中添加注解依赖

根据您需要使用的注解，在项目中添加相应的依赖：

**Swagger 3 (推荐)**:
```gradle
implementation("io.swagger.core.v3:swagger-annotations:2.2.20")
```

**Swagger 2**:
```gradle
implementation("io.swagger:swagger-annotations:1.6.12")
```

**POI Excel**:
```gradle
implementation("org.apache.poi:poi:5.2.5")
implementation("org.apache.poi:poi-ooxml:5.2.5")
```

**EasyExcel**:
```gradle
implementation("com.alibaba:easyexcel:3.3.2")
```

## 使用方法

### 基本使用

1. 在 Java/Kotlin 类的编辑器中，将光标放在类名或字段上
2. 按 `Alt+Enter`（或 `Option+Enter` on Mac）打开上下文菜单
3. 选择以下操作之一：
   - **Add Swagger Annotation** - 添加 Swagger 注解
   - **Add Excel Annotation** - 添加 Excel 注解
   - **Add Custom Annotation** - 添加自定义注解

### 示例

#### Java 示例

**处理前：**
```java
public class User {
    private Long userId;
    private String userName;
    private String email;
    private Date createTime;
    private Boolean isActive;
}
```

**按 Alt+Enter → Add Swagger Annotation 后：**
```java
public class User {
    @Schema(description = "用户ID")
    private Long userId;
    
    @Schema(description = "用户名称")
    private String userName;
    
    @Schema(description = "电子邮箱")
    private String email;
    
    @Schema(description = "创建时间")
    private Date createTime;
    
    @Schema(description = "是否激活")
    private Boolean isActive;
}
```

#### Kotlin 示例

**处理前：**
```kotlin
data class Product(
    val productId: Long,
    val productName: String,
    val price: BigDecimal,
    val stock: Int,
    val categoryId: Long
)
```

**按 Alt+Enter → Add Swagger Annotation 后：**
```kotlin
data class Product(
    @get:Schema(description = "产品ID")
    val productId: Long,
    
    @get:Schema(description = "产品名称")
    val productName: String,
    
    @get:Schema(description = "价格")
    val price: BigDecimal,
    
    @get:Schema(description = "库存数量")
    val stock: Int,
    
    @get:Schema(description = "分类ID")
    val categoryId: Long
)
```

### 高级用法

#### 1. 带已有注释的字段

如果字段已有 Javadoc/KDoc，插件会直接使用它：

```java
/**
 * 用户的唯一标识符
 */
private Long userId;
```

执行后：
```java
/**
 * 用户的唯一标识符
 */
@Schema(description = "用户的唯一标识符")
private Long userId;
```

#### 2. 已有注解的字段

如果字段已有目标注解，插件会跳过它：

```java
@Schema(description = "用户ID")
private Long userId;  // 不会重复添加
```

#### 3. 混合场景

```java
public class Order {
    /**
     * 订单编号
     */
    private String orderNo;  // 使用 Javadoc
    
    @ApiModelProperty("订单金额")
    private BigDecimal amount;  // 使用已有注解
    
    private Date createTime;  // AI 推测
}
```

执行 Add Swagger Annotation 后：
```java
public class Order {
    /**
     * 订单编号
     */
    @Schema(description = "订单编号")
    private String orderNo;
    
    @ApiModelProperty("订单金额")
    @Schema(description = "订单金额")
    private BigDecimal amount;
    
    @Schema(description = "创建时间")
    private Date createTime;
}
```

## 工作原理

### 注释获取流程

```
┌─────────────────┐
│  检查字段       │
└────────┬────────┘
         │
         ▼
┌─────────────────────┐
│ 是否已有目标注解？  │──Yes──> 跳过
└────────┬────────────┘
         │ No
         ▼
┌─────────────────────┐
│ 查找 Javadoc/KDoc   │──Found──> 使用注释
└────────┬────────────┘
         │ Not Found
         ▼
┌─────────────────────┐
│ 查找已有注解        │──Found──> 提取注释
│ (Swagger/Excel等)   │
└────────┬────────────┘
         │ Not Found
         ▼
┌─────────────────────┐
│ 收集到待处理列表    │
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│ AI 批量生成注释     │
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│ 添加注解到字段      │
└─────────────────────┘
```

### AI 调用机制

**批量处理模式（推荐）**：
- 收集所有无注释的字段
- 一次 AI 调用获取所有注释
- 效率高，成本低

**单个处理模式**：
- 每个字段独立调用 AI
- 适用于少量字段或测试

### Prompt 设计

插件使用精心设计的 Prompt 确保 AI 生成高质量注释：

```
请为以下Java/Kotlin类的字段生成简洁的中文注释。

字段列表:
- userId
- userName
- createTime

要求:
1. 返回JSON格式，key为字段名，value为注释文本
2. 注释要简洁明了，一般5-15字
3. 根据字段名的语义推测其用途

示例格式:
{
  "userId": "用户ID",
  "userName": "用户名称",
  "createTime": "创建时间"
}
```

## 支持的 AI 提供商

### DeepSeek (推荐)

**优势**：
- 专注代码理解，准确率高
- 价格便宜（0.14元/百万tokens）
- 响应速度快

**配置**：
```
AI 提供商: DeepSeek
API Key: sk-xxxxxxxxxxxxx
模型名称: deepseek-chat
API Base URL: https://api.deepseek.com
```

**获取 API Key**：https://platform.deepseek.com

### OpenAI

**优势**：
- GPT 系列强大的理解能力
- 支持多种模型选择

**配置**：
```
AI 提供商: OpenAI
API Key: sk-xxxxxxxxxxxxx
模型名称: gpt-3.5-turbo
API Base URL: https://api.openai.com
```

### Ollama (本地部署)

**优势**：
- 完全本地运行，无需 API Key
- 数据不出本地，隐私安全
- 免费使用

**配置**：
```
AI 提供商: Ollama
模型名称: qwen2.5-coder:7b
API Base URL: http://localhost:11434
```

**安装 Ollama**：https://ollama.com

### DashScope (阿里云)

**优势**：
- 国内访问速度快
- 支持通义千问系列模型

**配置**：
```
AI 提供商: DashScope
API Key: sk-xxxxxxxxxxxxx
模型名称: qwen-turbo
API Base URL: https://dashscope.aliyuncs.com
```

## 常见问题

### Q: AI 调用失败怎么办？

**A**: 检查以下项：
1. API Key 是否正确
2. 网络连接是否正常
3. API 配额是否充足
4. Base URL 是否正确
5. 查看 IDEA 的 Event Log 查看详细错误

### Q: 生成的注释不准确？

**A**: 
1. 尝试调整 Temperature 参数（推荐 0.3）
2. 为字段添加 Javadoc，插件会优先使用
3. 使用更强大的模型（如 GPT-4）
4. 手动修正后再提交

### Q: 支持哪些注解？

**A**: 
- 默认支持：Swagger (@Schema, @ApiModelProperty)、Excel (@ExcelProperty, @Excel)
- 自定义支持：可以在设置中配置任意注解模板

### Q: Kotlin 属性注解位置不对？

**A**: 插件会自动为 Kotlin 属性添加 `@get:` 前缀，确保注解在正确的位置。

### Q: 可以离线使用吗？

**A**: 可以，使用 Ollama 本地部署模型即可完全离线使用。

### Q: 批量处理大量字段会超时吗？

**A**: 
- 插件设置了 30 秒超时
- 建议分批处理（一次不超过 50 个字段）
- 或关闭批量处理，使用单个处理模式

## 注意事项

⚠️ **重要提醒**：

1. **API Key 安全**：不要将 API Key 提交到版本控制系统
2. **成本控制**：AI 调用会产生费用，注意使用量
3. **生成质量**：AI 生成的注释建议人工审核
4. **网络要求**：使用在线 AI 需要稳定的网络连接
5. **隐私考虑**：字段名会发送到 AI 服务器，注意敏感信息

## 项目结构

```
plugins/ai-annotator/
├── src/main/kotlin/site/addzero/aiannotator/
│   ├── settings/          # 设置系统
│   │   ├── AiAnnotatorSettings.kt
│   │   ├── AiAnnotatorSettingsService.kt
│   │   └── AiAnnotatorConfigurable.kt
│   ├── intention/         # Intention Actions
│   │   ├── base/         # 抽象基类
│   │   ├── swagger/      # Swagger 注解
│   │   ├── excel/        # Excel 注解
│   │   └── custom/       # 自定义注解
│   ├── util/             # 工具类
│   │   ├── AiService.kt  # AI 服务
│   │   ├── PsiUtil.kt    # PSI 工具
│   │   └── PsiPropertyUtil.kt
│   └── AiAnnotatorStartupActivity.kt
└── resources/
    └── META-INF/plugin.xml
```

## 开发

### 构建插件

```bash
./gradlew :plugins:ai-annotator:build
```

### 运行测试 IDE

```bash
./gradlew :plugins:ai-annotator:runIde
```

### 调试

在 IDEA 中创建 "Plugin" 运行配置，选择 ai-annotator 模块即可调试。

## 技术栈

- **语言**: Kotlin
- **框架**: IntelliJ Platform SDK
- **AI 集成**: HTTP Client + JSON
- **依赖**: Gson (JSON 解析)

## 贡献

欢迎提交 Issue 和 Pull Request！

## 许可证

与主项目相同。

## 联系方式

- 作者：zjarlin
- Email：zjarlin@outlook.com
- 项目：https://gitee.com/zjarlin/autoddl-idea-plugin

---

**祝您使用愉快！如果觉得有用，请给个 ⭐️**

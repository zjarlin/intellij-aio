# ShitCode Plugin - 项目结构

## 目录结构

```
plugins/shitcode/
├── .gitignore                                          # Git 忽略文件配置
├── build.gradle.kts                                   # Gradle 构建配置
├── README.md                                          # 项目说明文档
├── QUICKSTART.md                                      # 快速开始指南
├── MIGRATION.md                                       # 从 AutoDDL 迁移说明
├── PROJECT_STRUCTURE.md                               # 本文档
│
├── src/main/
│   ├── kotlin/site/addzero/shitcode/
│   │   ├── settings/                                  # 设置相关
│   │   │   ├── ShitCodeSettings.kt                   # 设置数据类 (5 行)
│   │   │   ├── ShitCodeSettingsService.kt            # 设置服务 (29 行)
│   │   │   └── ShitCodeConfigurable.kt               # 设置 UI (44 行)
│   │   │
│   │   └── toolwindow/                                # 工具窗口
│   │       └── ShitCodeToolWindow.kt                  # 主工具窗口 (256 行)
│   │
│   └── resources/
│       └── META-INF/
│           └── plugin.xml                             # 插件描述符
│
└── build/                                             # 构建输出目录 (git ignored)
    └── distributions/                                 # 插件发布包
        └── shitcode-*.zip
```

## 代码统计

| 文件 | 行数 | 功能 |
|------|------|------|
| ShitCodeToolWindow.kt | 256 | 工具窗口主逻辑 |
| ShitCodeConfigurable.kt | 44 | 设置界面 |
| ShitCodeSettingsService.kt | 29 | 设置持久化 |
| ShitCodeSettings.kt | 5 | 设置数据模型 |
| **总计** | **334** | - |

## 核心组件说明

### 1. ShitCodeSettings.kt

**职责**: 定义插件的配置数据结构

```kotlin
data class ShitCodeSettings(
    @JvmField var shitAnnotation: String = "Shit"
)
```

**说明**: 
- 简单的数据类，只包含一个配置项：注解名称
- 默认值为 "Shit"
- 使用 `@JvmField` 以便与 Java 互操作

---

### 2. ShitCodeSettingsService.kt

**职责**: 管理设置的持久化存储

**关键特性**:
- 实现 `PersistentStateComponent<ShitCodeSettings>` 接口
- 使用 `@State` 注解配置存储位置
- 提供单例访问方法

**存储位置**: 
- 文件: `~/.config/JetBrains/[IDE]/options/ShitCodeSettings.xml`
- 级别: Application Level (全局配置)

**使用方式**:
```kotlin
val settings = ShitCodeSettingsService.getInstance().state
val annotationName = settings.shitAnnotation
```

---

### 3. ShitCodeConfigurable.kt

**职责**: 提供设置界面 UI

**功能**:
- 在 `Settings → Tools → ShitCode` 中显示配置页面
- 提供文本框编辑注解名称
- 实现 `Configurable` 接口的标准生命周期方法

**UI 组件**:
- `JPanel` - 主面板
- `JTextField` - 注解名称输入框
- `JLabel` - 标签

---

### 4. ShitCodeToolWindow.kt

**职责**: 垃圾代码管理工具窗口的核心逻辑

#### 4.1 ShitCodeToolWindow (ToolWindowFactory)

创建并初始化工具窗口：
```kotlin
class ShitCodeToolWindow : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow)
}
```

#### 4.2 ShitCodePanel (主面板)

**UI 结构**:
```
┌─────────────────────────────────────────┐
│  [刷新] [删除选中] [全部删除]            │ ← 工具栏
├─────────────────────────────────────────┤
│  📁 垃圾代码列表                         │ ← 根节点
│  ├─ 📄 UserService.kt                   │
│  │  ├─ 类: LegacyUserService           │
│  │  └─ 函数: getUserById               │
│  ├─ 📄 PaymentService.kt               │
│  │  └─ 方法: processPayment            │
│  └─ ...                                 │
└─────────────────────────────────────────┘
```

**核心方法**:

| 方法 | 功能 |
|------|------|
| `refreshTree()` | 扫描项目，刷新垃圾代码列表 |
| `findAnnotatedElements()` | PSI 扫描，查找所有标记的元素 |
| `deleteSelectedNodes()` | 删除选中的代码元素 |
| `handleTreeNodeDoubleClick()` | 双击跳转到代码位置 |

**扫描逻辑**:
1. 检查索引状态（`DumbService.getInstance(project).isDumb`）
2. 获取项目范围（`GlobalSearchScope.projectScope(project)`）
3. 扫描 Kotlin 文件 (`KotlinFileType.INSTANCE`)
4. 扫描 Java 文件 (`JavaFileType.INSTANCE`)
5. 使用 `PsiTreeUtil.processElements()` 遍历元素
6. 检查注解匹配

**支持的元素类型**:

| 语言 | 支持的元素 |
|------|-----------|
| Kotlin | `KtClass`, `KtFunction`, `KtProperty` |
| Java | `PsiClass`, `PsiMethod`, `PsiField` |

#### 4.3 ElementInfo (数据类)

表示树节点的数据：
```kotlin
data class ElementInfo(val element: PsiElement) {
    override fun toString(): String
}
```

**显示格式**:
- Kotlin 类: "类: ClassName"
- Kotlin 函数: "函数: functionName"
- Kotlin 属性: "属性: propertyName"
- Java 类: "类: ClassName"
- Java 方法: "方法: methodName"
- Java 字段: "字段: fieldName"

---

### 5. plugin.xml

**插件描述符配置**:

```xml
<idea-plugin>
    <id>site.addzero.shitcode</id>
    <name>ShitCode</name>
    
    <!-- 依赖 -->
    <depends>com.intellij.modules.platform</depends>
    <depends>com.intellij.java</depends>
    <depends>org.jetbrains.kotlin</depends>
    
    <!-- 扩展点 -->
    <extensions defaultExtensionNs="com.intellij">
        <applicationService serviceImplementation="...ShitCodeSettingsService"/>
        <projectConfigurable instance="...ShitCodeConfigurable" 
                           displayName="ShitCode" 
                           parentId="tools"/>
        <toolWindow id="ShitCode" 
                   anchor="right" 
                   factoryClass="...ShitCodeToolWindow"
                   icon="AllIcons.General.Warning"/>
    </extensions>
</idea-plugin>
```

---

## 依赖关系

```
ShitCodeToolWindow
    ↓ 依赖
ShitCodeSettingsService
    ↓ 依赖
ShitCodeSettings

ShitCodeConfigurable
    ↓ 依赖
ShitCodeSettingsService
```

## 数据流

### 1. 设置修改流程

```
用户在 UI 中修改
    ↓
ShitCodeConfigurable.apply()
    ↓
ShitCodeSettingsService.state.shitAnnotation = newValue
    ↓
自动持久化到 XML 文件
```

### 2. 扫描流程

```
用户点击"刷新"按钮
    ↓
ShitCodePanel.refreshTree()
    ↓
findAnnotatedElements()
    ↓
扫描 Kotlin 文件 + 扫描 Java 文件
    ↓
PsiTreeUtil.processElements()
    ↓
检查注解匹配（使用 ShitCodeSettingsService.state.shitAnnotation）
    ↓
groupBy { containingFile }
    ↓
构建树形结构
    ↓
treeModel.reload()
```

### 3. 删除流程

```
用户选中节点并点击"删除选中"
    ↓
deleteSelectedNodes()
    ↓
收集 PsiElement 列表
    ↓
显示确认对话框
    ↓
WriteCommandAction.runWriteCommandAction {
    element.delete()
}
    ↓
refreshTree()
```

## 扩展点

当前插件暴露的扩展点：

| 扩展点 | 类型 | 说明 |
|--------|------|------|
| `com.intellij.applicationService` | Service | 设置服务 |
| `com.intellij.projectConfigurable` | Configurable | 设置 UI |
| `com.intellij.toolWindow` | ToolWindowFactory | 工具窗口 |

## 构建和发布

### Gradle 任务

```bash
# 编译
./gradlew :plugins:shitcode:compileKotlin

# 构建
./gradlew :plugins:shitcode:build

# 运行测试 IDE
./gradlew :plugins:shitcode:runIde

# 构建发布包
./gradlew :plugins:shitcode:buildPlugin

# 验证插件
./gradlew :plugins:shitcode:verifyPlugin
```

### 发布包内容

```
shitcode-VERSION.zip
├── lib/
│   └── shitcode-VERSION.jar
│       ├── site/addzero/shitcode/
│       │   ├── settings/
│       │   └── toolwindow/
│       └── META-INF/
│           └── plugin.xml
└── (其他依赖 JAR)
```

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Kotlin | 1.9+ | 编程语言 |
| IntelliJ Platform SDK | 2024.2.5+ | IDEA 插件开发框架 |
| Gradle | 8.x | 构建工具 |
| PSI (Program Structure Interface) | - | 代码结构分析 |
| Swing | - | UI 组件 |

## 性能考虑

1. **索引检查**: 在扫描前检查 `DumbService.isDumb` 避免索引未完成时扫描
2. **只读操作**: 扫描使用 `runReadAction` 包裹
3. **写操作**: 删除使用 `WriteCommandAction.runWriteCommandAction` 包裹
4. **范围限制**: 扫描限制在项目范围（`GlobalSearchScope.projectScope`）

## 测试建议

### 单元测试

- [ ] ShitCodeSettings 数据类测试
- [ ] ShitCodeSettingsService 持久化测试
- [ ] ElementInfo toString() 测试

### 集成测试

- [ ] Kotlin 注解扫描测试
- [ ] Java 注解扫描测试
- [ ] 删除操作测试
- [ ] 导航功能测试

### UI 测试

- [ ] 设置界面交互测试
- [ ] 工具窗口刷新测试
- [ ] 树节点双击测试

## 已知限制

1. **注解检查**: 只检查短名称（如 "Shit"），不检查完全限定名
2. **语言支持**: 仅支持 Java 和 Kotlin
3. **配置级别**: 配置为应用级别，不是项目级别
4. **扫描范围**: 仅扫描项目范围，不包括库和依赖

## 未来改进

- [ ] 支持项目级别配置
- [ ] 支持多个注解名称
- [ ] 添加注解参数过滤（如 `reason`）
- [ ] 支持正则表达式匹配
- [ ] 添加统计信息（总数、按文件分组统计）
- [ ] 导出为报告（HTML/Markdown）
- [ ] 与 TODO 工具窗口集成

## 贡献指南

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

---

**文档版本**: 1.0  
**最后更新**: 2025-11-23  
**维护者**: zjarlin

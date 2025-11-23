# Gradle Favorites 项目结构

```
gradle-favorites/
├── build.gradle.kts                    # 构建配置
├── README.md                           # 项目说明
├── USAGE.md                            # 使用指南
├── STRUCTURE.md                        # 本文件
│
└── src/main/
    ├── kotlin/site/addzero/gradle/favorites/
    │   │
    │   ├── model/                      # 数据模型层
    │   │   ├── FavoriteGradleTask.kt   # 收藏任务数据类
    │   │   ├── FavoriteTasksState.kt   # 持久化状态类
    │   │   └── FavoriteTaskData.kt     # XML 序列化数据类
    │   │
    │   ├── service/                    # 服务层
    │   │   └── GradleFavoritesService.kt   # 收藏管理服务(持久化)
    │   │
    │   ├── strategy/                   # 策略模式层
    │   │   ├── GradleTaskContextStrategy.kt        # 策略接口
    │   │   ├── AbstractGradleTaskContextStrategy.kt # 抽象基类
    │   │   ├── GradleToolWindowContextStrategy.kt  # Gradle面板策略
    │   │   ├── EditorContextStrategy.kt            # 编辑器策略
    │   │   └── GradleTaskStrategyRegistry.kt       # 策略注册表
    │   │
    │   ├── action/                     # 操作层
    │   │   ├── AddToFavoritesAction.kt             # 添加到收藏
    │   │   ├── RemoveFromFavoritesAction.kt        # 移除收藏
    │   │   ├── ExecuteFavoriteTaskAction.kt        # 执行任务
    │   │   ├── ShowFavoritesMenuAction.kt          # 显示收藏菜单
    │   │   └── ShowFavoritesNotificationAction.kt  # 显示通知
    │   │
    │   ├── ui/                         # UI 层
    │   │   ├── GradleFavoritesToolWindowFactory.kt # 工具窗口工厂
    │   │   └── GradleFavoritesPanel.kt             # 主面板UI
    │   │
    │   └── listener/                   # 监听器层
    │       └── EditorFileOpenListener.kt   # 文件打开监听器
    │
    └── resources/META-INF/
        └── plugin.xml                  # 插件描述文件

```

## 架构设计

### 1. 分层架构

```
┌─────────────────────────────────────────┐
│          UI Layer (ui/)                 │  用户界面
│  - ToolWindow                           │
│  - Panel                                │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│       Action Layer (action/)            │  用户操作
│  - Add/Remove/Execute                   │
│  - Menu Actions                         │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│     Strategy Layer (strategy/)          │  上下文策略
│  - GradleToolWindow Strategy            │
│  - Editor Strategy                      │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│      Service Layer (service/)           │  业务逻辑
│  - GradleFavoritesService               │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│       Model Layer (model/)              │  数据模型
│  - FavoriteGradleTask                   │
│  - State Classes                        │
└─────────────────────────────────────────┘
```

### 2. 策略模式设计

```kotlin
// 策略接口
interface GradleTaskContextStrategy {
    fun support(event): Boolean          // 是否支持当前上下文
    fun extractTaskInfo(event): Task?    // 提取任务信息
    fun getCurrentModulePath(event): String?  // 获取当前模块
    fun executeTask(project, task)       // 执行任务
}

// 策略注册表
class GradleTaskStrategyRegistry {
    private val strategies: List<Strategy> = [
        GradleToolWindowContextStrategy(),  // Gradle面板上下文
        EditorContextStrategy()             // 编辑器上下文
    ]
    
    fun findSupportedStrategy(event): Strategy? =
        strategies.firstOrNull { it.support(event) }
}
```

**使用流程:**
1. 用户触发操作 (如右键菜单)
2. 注册表遍历策略列表
3. 找到第一个 `support()` 返回 true 的策略
4. 使用该策略处理上下文

**优势:**
- ✅ 无 if-else 判断
- ✅ 易于扩展新上下文
- ✅ 符合开闭原则
- ✅ 策略独立测试

### 3. 数据持久化

```kotlin
@Service(Service.Level.PROJECT)
@State(
    name = "GradleFavoritesService",
    storages = [Storage("gradleFavorites.xml")]
)
class GradleFavoritesService : PersistentStateComponent<FavoriteTasksState>
```

**存储位置:** `.idea/gradleFavorites.xml`

**数据格式:**
```xml
<project>
  <component name="GradleFavoritesService">
    <option name="favorites">
      <list>
        <FavoriteTaskData>
          <option name="projectPath" value=":lib:tool-psi" />
          <option name="taskName" value="kspKotlin" />
        </FavoriteTaskData>
      </list>
    </option>
  </component>
</project>
```

### 4. 事件流

#### 4.1 添加收藏流程

```
用户点击 "Add Favorite"
    ↓
GradleFavoritesPanel.showAddDialog()
    ↓
用户输入 模块路径 + 任务名
    ↓
创建 FavoriteGradleTask
    ↓
GradleFavoritesService.addFavorite()
    ↓
数据持久化到 XML
    ↓
刷新 UI 列表
```

#### 4.2 执行收藏流程

```
用户在编辑器右键
    ↓
ShowFavoritesMenuAction.update()
    ↓
获取当前模块路径 (EditorContextStrategy)
    ↓
查询该模块的收藏任务
    ↓
动态生成菜单项 (getChildren)
    ↓
用户点击任务
    ↓
ExecuteFavoriteTaskAction.actionPerformed()
    ↓
策略执行任务 (executeTask)
    ↓
显示通知
```

#### 4.3 文件打开通知流程

```
用户打开文件
    ↓
EditorFileOpenListener.fileOpened()
    ↓
ModuleUtil.findModuleForFile()
    ↓
转换模块名 → Gradle 路径
    ↓
检查是否已显示通知 (去重)
    ↓
查询该模块的收藏任务
    ↓
ShowFavoritesNotificationAction.showNotification()
    ↓
显示可交互通知气泡
```

### 5. 核心类职责

| 类 | 职责 | 依赖 |
|---|---|---|
| **GradleFavoritesService** | 管理收藏列表,持久化 | Model |
| **GradleTaskStrategyRegistry** | 策略注册和查找 | Strategy |
| **EditorContextStrategy** | 编辑器上下文处理 | - |
| **GradleFavoritesPanel** | 主UI面板 | Service |
| **ShowFavoritesMenuAction** | 动态菜单生成 | Service, Strategy |
| **ExecuteFavoriteTaskAction** | 任务执行 | Strategy |
| **EditorFileOpenListener** | 文件打开监听 | Service |

### 6. 扩展点

#### 6.1 添加新的上下文策略

```kotlin
class CustomContextStrategy : AbstractGradleTaskContextStrategy() {
    override fun support(event: AnActionEvent): Boolean {
        // 判断逻辑
    }
    
    // 实现其他方法...
}

// 在 GradleTaskStrategyRegistry 中注册
private val strategies = listOf(
    GradleToolWindowContextStrategy(),
    EditorContextStrategy(),
    CustomContextStrategy()  // 新增
)
```

#### 6.2 添加新的操作

1. 创建 Action 类继承 `AnAction`
2. 在 `plugin.xml` 中注册
3. 使用 `GradleFavoritesService` 访问数据

#### 6.3 自定义通知

```kotlin
NotificationGroupManager.getInstance()
    .getNotificationGroup("Gradle Favorites")
    .createNotification(title, content, type)
    .addAction(customAction)
    .notify(project)
```

## 依赖关系

```
plugin.xml
    ↓
┌───────────────────────────────────────┐
│  Services (注册)                       │
│  - GradleFavoritesService             │
│  - GradleTaskStrategyRegistry         │
└───────────────────────────────────────┘
    ↓
┌───────────────────────────────────────┐
│  UI Components                        │
│  - ToolWindow                         │
│  - Actions                            │
└───────────────────────────────────────┘
    ↓
┌───────────────────────────────────────┐
│  Strategies                           │
│  - Context Detection                  │
│  - Task Execution                     │
└───────────────────────────────────────┘
```

## 技术栈

- **语言:** Kotlin
- **框架:** IntelliJ Platform SDK
- **UI:** Swing (JBList, JPanel)
- **依赖注入:** IntelliJ Service
- **持久化:** PersistentStateComponent
- **通知:** NotificationGroup
- **构建:** Gradle + IntelliJ Platform Gradle Plugin

## 设计模式

1. **策略模式** (Strategy): 上下文处理策略
2. **工厂模式** (Factory): ToolWindowFactory
3. **观察者模式** (Observer): FileEditorManagerListener
4. **单例模式** (Singleton): Service 服务
5. **MVC 模式**: Model-View-Controller 分层

## 代码统计

- Kotlin 文件: 14 个
- 总代码行数: ~800 行
- 包结构: 6 个包
- 测试覆盖: 待添加

## 未来优化

1. ✅ 支持从 Gradle 面板直接添加收藏 (需要深入集成 Gradle API)
2. ⬜ 添加任务执行历史记录
3. ⬜ 支持任务分组和排序
4. ⬜ 导入/导出收藏列表
5. ⬜ 添加任务执行快捷键
6. ⬜ 支持任务执行前确认
7. ⬜ 集成 Gradle 任务输出显示

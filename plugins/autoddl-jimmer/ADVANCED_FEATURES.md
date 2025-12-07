# AutoDDL Jimmer - 高级功能

## 🎯 新增功能

### 1. 独立日志面板 ✅

**位置**: 底部工具窗口 `Jimmer DDL`

**功能**:
- ✅ 表格化展示执行日志
- ✅ 记录每条SQL执行情况（成功/失败）
- ✅ 实时统计（总计/成功/失败）
- ✅ 日志导出功能
- ✅ 清空日志
- ✅ 彩色状态显示（绿色=成功，红色=失败，蓝色=执行中）

#### 日志类型

| 类型 | 描述 | 示例 |
|------|------|------|
| GENERATE | DDL生成 | 开始生成差量DDL，共 5 个实体 |
| EXECUTE | SQL执行 | CREATE TABLE `user` ... |
| BATCH | 批量执行 | 批量执行完成：总计 10 条 |
| ERROR | 错误 | 数据源连接失败 |

#### 日志面板截图

```
时间                  类型      状态      SQL/消息                           详情
2025-12-07 14:30:22  GENERATE  RUNNING   开始生成差量DDL，共 5 个实体
2025-12-07 14:30:25  GENERATE  SUCCESS   DDL生成完成，共 15 条语句         /path/to/delta_xxx.sql
2025-12-07 14:30:26  EXECUTE   SUCCESS   CREATE TABLE `sys_user` ...      OK
2025-12-07 14:30:26  EXECUTE   SUCCESS   CREATE UNIQUE INDEX ...          OK
2025-12-07 14:30:27  EXECUTE   FAILED    ALTER TABLE `order` ...          Table 'order' doesn't exist
2025-12-07 14:30:28  BATCH     PARTIAL   批量执行完成：总计 15 条          成功 14, 失败 1

工具栏: [清空日志] [导出日志]  总计: 6  成功: 5  失败: 1
```

#### 使用方式

1. **查看日志**
   - `View → Tool Windows → Jimmer DDL`
   - 或点击底部工具栏的 `Jimmer DDL` 标签

2. **导出日志**
   - 点击工具栏的 `导出日志` 按钮
   - 选择保存位置
   - 生成文本格式日志文件

3. **清空日志**
   - 点击 `清空日志` 按钮
   - 统计数据也会重置

#### 代码集成

```kotlin
// 获取日志面板
val logPanel = JimmerDdlToolWindowFactory.getLogPanel(project)

// 记录生成开始
logPanel?.logGenerationStart(entityCount = 5)

// 记录生成完成
logPanel?.logGenerationComplete(
    outputFile = "/path/to/delta.sql",
    statementCount = 15
)

// 记录SQL执行
logPanel?.logSqlExecution(
    sql = "CREATE TABLE `user` ...",
    success = true
)

// 记录批量执行
logPanel?.logBatchExecution(
    totalCount = 15,
    successCount = 14,
    failedCount = 1
)

// 记录错误
logPanel?.logError(
    message = "数据源连接失败",
    details = "Connection timeout"
)
```

---

### 2. 实体变更通知 ✅

**位置**: 右上角状态栏（类似 Gradle 的小图标）

**功能**:
- ✅ 实时监听 Jimmer 实体文件变更
- ✅ 检测到变更后显示执行图标
- ✅ 点击图标快速重新生成 DDL
- ✅ 防抖机制（2秒内多次变更只通知一次）
- ✅ 生成后自动清除通知

#### 工作流程

```
Jimmer 实体文件变更
   ↓
[JimmerEntityChangeListener]
   ↓
检测 @Entity 注解
   ↓
[防抖: 2秒内只触发一次]
   ↓
[EntityChangeNotifier]
   ↓
显示右上角图标 ⚡
   ↓
用户点击图标
   ↓
[RegenerateDdlAction]
   ↓
重新生成 DDL
   ↓
清除图标
```

#### 监听的文件类型

| 文件类型 | 检测条件 |
|---------|---------|
| `.java` | 包含 `@Entity` 注解 |
| `.kt` | 包含 `@Entity` 注解 |

支持的注解：
- `org.babyfish.jimmer.sql.Entity`
- `javax.persistence.Entity`
- `jakarta.persistence.Entity`

#### 使用示例

1. **修改实体文件**
   ```kotlin
   @Entity
   class User(
       @Id val id: Long,
       @Key val username: String,
       val newField: String  // 新增字段
   )
   ```

2. **保存文件**
   - 2秒后，右上角出现执行图标 ⚡

3. **点击图标**
   - 自动触发 `Generate Delta DDL`
   - 图标消失
   - 日志面板显示生成记录

#### 状态栏 Widget

**图标**:
- 有变更：⚡ (执行图标)
- 无变更：隐藏

**Tooltip**:
```
Jimmer 实体已变更，点击重新生成 DDL
```

**位置**:
```
[Git] [JimmerDDL⚡] [其他插件...]
```

#### 代码实现

**监听器**:
```kotlin
class JimmerEntityChangeListener(private val project: Project) : BulkFileListener {
    
    private val notifier = EntityChangeNotifier.getInstance(project)
    private var lastChangeTime = 0L
    private val DEBOUNCE_DELAY = 2000L
    
    override fun after(events: List<VFileEvent>) {
        val entityFileChanges = events.filter { isEntityFile(it.file) }
        
        if (entityFileChanges.isNotEmpty()) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastChangeTime > DEBOUNCE_DELAY) {
                lastChangeTime = currentTime
                notifier.notifyEntityChanged(entityFileChanges.size)
            }
        }
    }
}
```

**通知器**:
```kotlin
@Service(Service.Level.PROJECT)
class EntityChangeNotifier(private val project: Project) {
    
    @Volatile
    private var hasChanges = false
    
    fun notifyEntityChanged(fileCount: Int) {
        hasChanges = true
        updateWidget()
    }
    
    fun clearChanges() {
        hasChanges = false
        updateWidget()
    }
}
```

**Widget**:
```kotlin
class EntityChangeWidget(private val project: Project) : StatusBarWidget {
    
    inner class IconPresentation : StatusBarWidget.IconPresentation {
        
        override fun getIcon(): Icon? {
            val notifier = EntityChangeNotifier.getInstance(project)
            return if (notifier.hasChanges()) {
                AllIcons.Actions.Execute
            } else {
                null  // 隐藏
            }
        }
        
        override fun getClickConsumer(): Consumer<MouseEvent>? {
            return Consumer { event ->
                RegenerateDdlAction().actionPerformed(...)
            }
        }
    }
}
```

---

## 🎨 UI 组件

### 日志面板 (DdlLogPanel)

**布局**:
```
┌─────────────────────────────────────────────────────┐
│ [清空日志] [导出日志]  总计: 10  成功: 9  失败: 1   │ ← 工具栏
├─────────────────────────────────────────────────────┤
│ 时间          类型      状态     SQL/消息      详情  │ ← 表头
├─────────────────────────────────────────────────────┤
│ 14:30:22   GENERATE  RUNNING  开始生成...          │
│ 14:30:25   GENERATE  SUCCESS  DDL生成完成...       │ ← 日志行
│ 14:30:26   EXECUTE   SUCCESS  CREATE TABLE...  OK  │
│ 14:30:27   EXECUTE   FAILED   ALTER TABLE...  Error│
│ ...                                                  │
└─────────────────────────────────────────────────────┘
```

**特性**:
- 自动滚动到最新日志
- 状态列彩色显示
- 单元格不可编辑
- 支持选中复制

### 状态栏图标 (EntityChangeWidget)

**状态**:
```
无变更:  [Git] [其他...]
有变更:  [Git] [⚡JimmerDDL] [其他...]
         ↑
         点击重新生成
```

**交互**:
- 鼠标悬停：显示 Tooltip
- 单击：触发重新生成
- 生成后：自动隐藏

---

## 🔧 配置

### plugin.xml 配置

```xml
<extensions defaultExtensionNs="com.intellij">
    <!-- 状态栏 Widget -->
    <statusBarWidgetFactory 
        id="JimmerDdl.EntityChange"
        implementation="site.addzero.autoddl.jimmer.notification.EntityChangeWidgetFactory"
        order="after git"/>
</extensions>

<projectListeners>
    <!-- 文件变更监听器 -->
    <listener 
        class="site.addzero.autoddl.jimmer.listener.JimmerEntityChangeListener"
        topic="com.intellij.openapi.vfs.newvfs.BulkFileListener"/>
</projectListeners>
```

### 防抖配置

```kotlin
// JimmerEntityChangeListener.kt
private val DEBOUNCE_DELAY = 2000L  // 2秒防抖
```

可以根据需要调整防抖时间。

---

## 📊 完整工作流程

### 场景：修改实体并重新生成

```
1. 开发者修改 User.kt，添加新字段
   ↓
2. 保存文件
   ↓
3. [JimmerEntityChangeListener] 检测到变更
   ↓
4. 右上角出现 ⚡ 图标
   ↓
5. 开发者点击图标
   ↓
6. [GenerateDeltaDdlAction] 触发
   ↓
7. [日志面板] 记录：
   - 14:35:10  GENERATE  RUNNING   开始生成差量DDL，共 5 个实体
   - 14:35:12  GENERATE  SUCCESS   DDL生成完成，共 16 条语句
   ↓
8. [自动执行] (如果配置)
   ↓
9. [日志面板] 记录：
   - 14:35:13  EXECUTE   SUCCESS   CREATE TABLE `sys_user` ...
   - 14:35:13  EXECUTE   SUCCESS   ALTER TABLE `sys_user` ADD COLUMN `new_field` ...
   - 14:35:14  BATCH     SUCCESS   批量执行完成：总计 16 条
   ↓
10. ⚡ 图标消失
    ↓
11. [通知] 弹出：SQL 执行成功：成功 16 条
```

---

## 🎯 用户体验提升

### 之前
- ❌ 不知道SQL执行了哪些语句
- ❌ 不知道哪条SQL失败了
- ❌ 实体改动后需要手动触发生成
- ❌ 没有历史记录

### 现在
- ✅ 清晰的日志面板，每条SQL一目了然
- ✅ 失败的SQL红色标记，附带错误详情
- ✅ 实体改动自动提示，一键重新生成
- ✅ 日志可导出，方便问题排查
- ✅ 实时统计，知道成功率

---

## 🚀 后续优化

### 短期
- [ ] 点击日志行显示完整SQL（对话框）
- [ ] 日志搜索/过滤功能
- [ ] 支持暂停/继续执行
- [ ] 确认对话框（执行前预览SQL）

### 中期
- [ ] 日志分级（INFO/WARN/ERROR）
- [ ] 执行进度条（逐条显示）
- [ ] 回滚功能（一键回滚失败的变更）
- [ ] 历史记录（保存最近N次执行）

### 长期
- [ ] SQL执行可视化（图表）
- [ ] 性能分析（执行时间统计）
- [ ] 对比视图（变更前后对比）
- [ ] 团队协作（共享执行日志）

---

## 📚 技术细节

### 日志面板实现

**表格模型**:
```kotlin
class LogTableModel : DefaultTableModel(
    arrayOf("时间", "类型", "状态", "SQL/消息", "详情"),
    0
) {
    override fun isCellEditable(row: Int, column: Int) = false
}
```

**彩色渲染**:
```kotlin
table.columnModel.getColumn(2).cellRenderer = object : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(...): Component {
        val component = super.getTableCellRendererComponent(...)
        when (value.toString()) {
            "SUCCESS" -> component.foreground = Color(0, 128, 0)
            "FAILED" -> component.foreground = Color.RED
            "RUNNING" -> component.foreground = Color.BLUE
        }
        return component
    }
}
```

### 文件监听实现

**过滤实体文件**:
```kotlin
private fun isEntityFile(file: VirtualFile): Boolean {
    if (!file.name.endsWith(".java") && !file.name.endsWith(".kt")) {
        return false
    }
    
    val psiFile = PsiManager.getInstance(project).findFile(file) ?: return false
    
    if (psiFile is PsiJavaFile) {
        return psiFile.classes.any { 
            it.annotations.any { annotation ->
                isEntityAnnotation(annotation.qualifiedName)
            }
        }
    }
    
    // Kotlin 类似处理
}
```

### 状态栏Widget实现

**动态显示/隐藏**:
```kotlin
override fun getIcon(): Icon? {
    return if (notifier.hasChanges()) {
        AllIcons.Actions.Execute  // 显示
    } else {
        null  // 隐藏
    }
}
```

**点击处理**:
```kotlin
override fun getClickConsumer(): Consumer<MouseEvent>? {
    return Consumer { event ->
        if (notifier.hasChanges()) {
            RegenerateDdlAction().actionPerformed(...)
            notifier.clearChanges()  // 清除标记
        }
    }
}
```

---

**实现时间**: 2025-12-07  
**功能状态**: ✅ 完成  
**测试状态**: 待测试  
**文档状态**: ✅ 已完善

# AutoDDL Jimmer 插件实现总结

## 🎯 项目目标

创建一个 IntelliJ IDEA 插件，用于：
1. 扫描 Jimmer 实体类
2. 生成差量 DDL
3. 从 Database 插件获取连接信息
4. 使用 `SqlExecutor` 工具类执行 SQL
5. 生成目录可配置
6. 自动执行可配置

## ✅ 已完成功能

### 1. 项目结构 ✅

```
plugins/autoddl-jimmer/
├── build.gradle.kts                          # 构建配置
├── README.md                                  # 使用文档
├── IMPLEMENTATION_SUMMARY.md                  # 本文档
└── src/main/
    ├── kotlin/site/addzero/autoddl/jimmer/
    │   ├── settings/                          # 配置管理
    │   │   ├── JimmerDdlSettings.kt          # 配置数据类
    │   │   └── JimmerDdlConfigurable.kt      # 配置UI
    │   ├── service/                           # 核心服务
    │   │   ├── DeltaDdlGenerator.kt          # DDL生成
    │   │   └── SqlExecutionService.kt        # SQL执行
    │   ├── action/                            # 用户操作
    │   │   ├── GenerateDeltaDdlAction.kt     # 生成DDL
    │   │   └── GenerateAndExecuteDdlAction.kt # 生成并执行
    │   └── toolwindow/                        # 工具窗口
    │       └── JimmerDdlToolWindowFactory.kt
    └── resources/META-INF/
        └── plugin.xml                         # 插件描述符
```

### 2. 核心组件实现

#### A. 配置管理 (`JimmerDdlSettings`)

**功能**：
- 持久化插件配置
- 使用 IntelliJ 的 `PersistentStateComponent`

**配置项**：
```kotlin
var outputDirectory: String = ".autoddl/jimmer"     // DDL输出目录
var autoExecute: Boolean = false                    // 是否自动执行
var confirmBeforeExecute: Boolean = true            // 执行前确认
var generateRollback: Boolean = true                // 生成回滚SQL
var dataSourceName: String = ""                     // 数据源名称
var includeIndexes: Boolean = true                  // 包含索引
var includeForeignKeys: Boolean = true              // 包含外键
var includeComments: Boolean = true                 // 包含注释
var scanPackages: String = "com.example.entity"     // 扫描包路径
```

#### B. DDL 生成服务 (`DeltaDdlGenerator`)

**功能**：
1. **扫描实体类**
   ```kotlin
   fun scanJimmerEntities(): List<LsiClass>
   ```
   - 扫描配置的包路径
   - 查找带 `@Entity` 注解的类
   - 支持 Jimmer 和 JPA 注解
   
2. **生成 DDL**
   ```kotlin
   fun generateDeltaDdl(entities: List<LsiClass>, databaseType: DatabaseType): DdlResult
   ```
   - 使用 `toCompleteSchemaDDL()` 生成完整 Schema
   - 按阶段生成（表 → 索引 → 外键 → 注释）
   - 保存到配置的输出目录
   - 添加时间戳

3. **生成回滚 SQL**
   ```kotlin
   private fun generateRollbackSql(entities: List<LsiClass>, databaseType: DatabaseType): String
   ```
   - 生成 `DROP TABLE` 语句
   - 用于出错时回滚

#### C. SQL 执行服务 (`SqlExecutionService`)

**关键实现**：

1. **获取数据源连接信息**
   ```kotlin
   private fun getDataSource(): LocalDataSource?
   private fun extractConnectionInfo(dataSource: LocalDataSource): ConnectionInfo
   ```
   - 从 IntelliJ Database 插件获取数据源
   - 提取 URL、用户名、密码

2. **执行 SQL**
   ```kotlin
   fun executeSqlFile(sqlFile: File): ExecutionResult
   ```
   - 读取 SQL 文件
   - 解析 SQL 语句（按分号分割）
   - 使用 `SqlExecutor.execute()` 执行
   - 统计成功/失败数量

3. **使用 SqlExecutor 工具类**
   ```kotlin
   SqlExecutor.execute(
       url = connectionInfo.url,
       username = connectionInfo.username,
       password = connectionInfo.password,
       sql = sql
   )
   ```

#### D. 用户操作 (`Action`)

**两个 Action**：

1. **GenerateDeltaDdlAction** - 仅生成
   - 扫描实体
   - 生成 DDL
   - 保存文件
   - 根据配置决定是否执行

2. **GenerateAndExecuteDdlAction** - 生成并执行
   - 扫描实体
   - 生成 DDL
   - 立即执行
   - 显示执行结果

**后台任务**：
```kotlin
ProgressManager.getInstance().run(object : Task.Backgroundable(...) {
    override fun run(indicator: ProgressIndicator) {
        indicator.text = "扫描 Jimmer 实体类..."
        indicator.fraction = 0.2
        // ...
    }
})
```

#### E. 配置界面 (`JimmerDdlConfigurable`)

**UI 组件**：
- 文本框：输出目录、数据源名称、扫描包路径
- 复选框：自动执行、确认、回滚、索引、外键、注释
- 使用 `FormBuilder` 构建

#### F. 工具窗口 (`JimmerDdlToolWindowFactory`)

**功能**：
- 显示使用说明
- 显示操作日志
- 位于底部面板

### 3. 依赖配置

**build.gradle.kts**：
```kotlin
dependencies {
    // LSI 核心
    implementation(project(":checkouts:metaprogramming-lsi:lsi-core"))
    implementation(project(":checkouts:metaprogramming-lsi:lsi-database"))
    implementation(project(":checkouts:metaprogramming-lsi:lsi-intellij"))
    implementation(project(":checkouts:metaprogramming-lsi:lsi-psi"))
    implementation(project(":checkouts:metaprogramming-lsi:lsi-kt"))
    
    // DDL Generator
    implementation(project(":lib:ddlgenerator:tool-ddlgenerator"))
    
    // SQL Executor (关键)
    implementation("site.addzero:tool-sql-executor:2025.11.26")
    
    // UI 组件
    implementation(project(":lib:tool-swing"))
    implementation(project(":lib:tool-awt"))
}
```

**plugin.xml**：
```xml
<!-- 依赖 Database 插件 -->
<depends>com.intellij.database</depends>
```

### 4. 工作流程

```
用户操作
   ↓
[Action: Generate DDL]
   ↓
扫描包路径 → 查找 @Entity 类 → 转换为 LsiClass
   ↓
生成 DDL → toCompleteSchemaDDL()
   ↓
保存到文件 → .autoddl/jimmer/delta_20251207_123456.sql
   ↓
[如果配置了自动执行]
   ↓
从 Database 插件获取连接 → extractConnectionInfo()
   ↓
使用 SqlExecutor 执行 → SqlExecutor.execute()
   ↓
显示结果 → Notification
```

## 📝 使用示例

### 配置

1. **Settings → Tools → AutoDDL Jimmer**
   ```
   DDL输出目录: .autoddl/jimmer
   数据源名称: mysql@localhost
   扫描包路径: com.example.entity,com.example.domain
   ✓ 自动执行
   ✓ 执行前确认
   ✓ 生成回滚SQL
   ✓ 包含索引
   ✓ 包含外键
   ✓ 包含注释
   ```

2. **Database 插件配置**
   ```
   名称: mysql@localhost
   URL: jdbc:mysql://localhost:3306/mydb
   用户: root
   密码: ******
   ```

### 实体示例

```kotlin
package com.example.entity

@Entity
@Table(name = "sys_user")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @Key
    val username: String,
    
    @Key(group = "tenant_email")
    val tenantId: Long,
    
    @Key(group = "tenant_email")
    val email: String,
    
    val password: String,
    val createTime: LocalDateTime
)
```

### 生成的 DDL

**文件**: `.autoddl/jimmer/delta_20251207_143022.sql`

```sql
-- =============================================
-- Phase 1: Create All Tables (without FK)
-- =============================================

-- Table: User
CREATE TABLE `sys_user` (
  `id` BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  `username` VARCHAR(255),
  `tenantId` BIGINT,
  `email` VARCHAR(255),
  `password` VARCHAR(255),
  `createTime` DATETIME
);

-- =============================================
-- Phase 2: Create Indexes
-- =============================================

-- Indexes for User
CREATE UNIQUE INDEX `uk_user_username` ON `sys_user` (`username`);
CREATE UNIQUE INDEX `uk_user_tenant_email` ON `sys_user` (`tenantId`, `email`);

-- =============================================
-- Phase 3: Add Foreign Key Constraints
-- =============================================

-- =============================================
-- Phase 4: Add Comments
-- =============================================
```

### 执行结果

```
✓ CREATE TABLE `sys_user` ...
✓ CREATE UNIQUE INDEX `uk_user_username` ...
✓ CREATE UNIQUE INDEX `uk_user_tenant_email` ...

执行完成：成功 3 条，失败 0 条
```

## 🔑 关键技术点

### 1. 从 Database 插件获取连接

```kotlin
val connectionManager = DatabaseConnectionManager.getInstance()
val dataSources = connectionManager.getDataSources(project)
val dataSource = dataSources.filterIsInstance<LocalDataSource>()
    .firstOrNull { it.name == dataSourceName }

val url = dataSource.url
val username = dataSource.username  
val password = dataSource.password
```

### 2. 使用 SqlExecutor 执行

```kotlin
import site.addzero.util.db.SqlExecutor

SqlExecutor.execute(
    url = "jdbc:mysql://localhost:3306/mydb",
    username = "root",
    password = "password",
    sql = "CREATE TABLE ..."
)
```

### 3. 后台任务

```kotlin
ProgressManager.getInstance().run(
    object : Task.Backgroundable(project, "任务名称", true) {
        override fun run(indicator: ProgressIndicator) {
            indicator.text = "进度信息"
            indicator.fraction = 0.5
            // 执行任务
        }
        
        override fun onSuccess() {
            // 成功回调
        }
        
        override fun onThrowable(error: Throwable) {
            // 失败回调
        }
    }
)
```

### 4. 通知

```kotlin
NotificationGroupManager.getInstance()
    .getNotificationGroup("AutoDDL.Jimmer")
    .createNotification(content, type)
    .notify(project)
```

## 🚀 下一步计划

### 短期（必要功能）
- [ ] 添加数据库类型自动识别（根据数据源 URL）
- [ ] 完善实体扫描（支持 Kotlin 文件）
- [ ] 添加确认对话框（执行前）

### 中期（增强功能）
- [ ] 差量检测（对比现有表结构）
- [ ] 只生成变更部分（新增列、修改列等）
- [ ] 执行历史记录
- [ ] SQL 预览界面

### 长期（高级功能）
- [ ] 可视化表结构对比
- [ ] 自定义 DDL 模板
- [ ] 批量操作（多项目）
- [ ] 回滚功能增强

## ⚠️ 注意事项

### 1. Database 插件依赖

插件依赖 IntelliJ IDEA 的 Database 插件：
```xml
<depends>com.intellij.database</depends>
```

确保 Database 插件已启用并配置好数据源。

### 2. SqlExecutor 依赖

需要在 Maven 仓库中可用：
```kotlin
implementation("site.addzero:tool-sql-executor:2025.11.26")
```

如果该依赖不在公共仓库，需要：
1. 发布到私有 Maven 仓库
2. 或者使用 `mavenLocal()`

### 3. 实体扫描

当前实现使用 `AnnotatedElementsSearch`，可能有限制。
如果扫描不到实体，可以改用：
- `FilenameIndex` 扫描文件
- `PsiTreeUtil` 遍历 PSI 树

### 4. SQL 解析

当前使用简单的分号分割，可能不适用于：
- 存储过程（包含多个分号）
- 复杂 SQL（字符串中包含分号）

建议使用专业的 SQL 解析器（如 JSqlParser）。

## 📦 构建和部署

### 构建插件

```bash
cd /Users/zjarlin/IdeaProjects/intellij-aio
./gradlew :plugins:autoddl-jimmer:buildPlugin
```

生成的插件位于：
```
plugins/autoddl-jimmer/build/distributions/autoddl-jimmer-1.0.0.zip
```

### 安装

1. `Settings → Plugins → Install Plugin from Disk`
2. 选择生成的 ZIP 文件
3. 重启 IDE

### 测试

1. 打开包含 Jimmer 实体的项目
2. 配置数据源（Database 插件）
3. 配置插件（Settings → AutoDDL Jimmer）
4. 右键项目 → Generate Delta DDL
5. 查看生成的 SQL 文件

## 🎓 学习价值

通过本项目，你学到了：

1. **IntelliJ 插件开发**
   - Action 注册
   - Service 和 Settings
   - Tool Window
   - Notification

2. **PSI 操作**
   - 扫描注解类
   - 提取类信息
   - 转换为 LSI

3. **数据库操作**
   - 从 Database 插件获取连接
   - 执行 SQL
   - 错误处理

4. **DDL 生成**
   - 实体扫描
   - 索引生成
   - 外键生成
   - 多对多中间表

## 📚 相关文档

- [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/welcome.html)
- [Database Tool Support](https://plugins.jetbrains.com/docs/intellij/database.html)
- [Jimmer Documentation](https://babyfish-ct.github.io/jimmer-doc/)
- [DDL Generator](../tool-ddlgenerator/ENHANCED_FEATURES.md)

---

**创建时间**: 2025-12-07  
**作者**: zjarlin  
**版本**: 1.0.0  
**状态**: ✅ 初始实现完成

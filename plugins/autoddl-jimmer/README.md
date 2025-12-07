# AutoDDL for Jimmer

IntelliJ IDEA 插件，用于 Jimmer 框架的 DDL 差量生成和自动执行。

## 功能特性

### 核心功能
- ✅ 自动扫描 Jimmer 实体类（`@Entity`）
- ✅ 生成差量 DDL（CREATE/ALTER TABLE）
- ✅ 支持索引生成（`@Key`, `@Key(group=)`）
- ✅ 支持外键生成（`@ManyToOne`, `@JoinColumn`）
- ✅ 支持多对多中间表（`@ManyToMany`）
- ✅ 可选自动执行 SQL
- ✅ 生成回滚 SQL

### 配置选项
- DDL 输出目录（可配置）
- 是否自动执行
- 执行前确认
- 生成回滚 SQL
- 包含索引/外键/注释
- 扫描包路径

## 安装

### 依赖
1. **Database 插件**（IntelliJ 内置）
2. **tool-sql-executor** (自动依赖)

### 构建
```bash
./gradlew :plugins:autoddl-jimmer:buildPlugin
```

## 使用方式

### 1. 配置数据源
在 IntelliJ IDEA 中配置数据库连接：
- `View → Tool Windows → Database`
- 添加数据源（MySQL, PostgreSQL, Oracle 等）

### 2. 配置插件
`Settings → Tools → AutoDDL Jimmer`

配置项：
- **DDL 输出目录**：相对于项目根目录，例如 `.autoddl/jimmer`
- **数据源名称**：从 Database 插件中配置的数据源名称
- **扫描包路径**：多个包用逗号分隔，例如 `com.example.entity,com.example.domain`
- **自动执行**：是否在生成后自动执行 DDL
- **执行前确认**：自动执行时是否需要确认
- **生成回滚 SQL**：是否同时生成回滚脚本
- **包含索引/外键/注释**：DDL 包含的内容

### 3. 生成 DDL

#### 方式一：右键菜单
1. 在项目视图中右键点击项目
2. 选择 `Generate Delta DDL`
3. 等待生成完成
4. 查看输出目录的 DDL 文件

#### 方式二：工具菜单
1. `Tools → Generate & Execute DDL`
2. 自动生成并执行

#### 方式三：工具窗口
1. `View → Tool Windows → Jimmer DDL`
2. 查看日志和操作面板

### 4. 生成的文件

**DDL 文件**：`.autoddl/jimmer/delta_20251207_123456.sql`
```sql
-- =============================================
-- Phase 1: Create All Tables (without FK)
-- =============================================

CREATE TABLE `user` (
  `id` BIGINT NOT NULL PRIMARY KEY,
  `username` VARCHAR(255)
);

-- =============================================
-- Phase 2: Create Indexes
-- =============================================

CREATE UNIQUE INDEX `uk_user_username` ON `user` (`username`);

-- =============================================
-- Phase 3: Add Foreign Key Constraints
-- =============================================

...
```

**回滚文件**（如果开启）：`.autoddl/jimmer/rollback_20251207_123456.sql`

## 实体示例

```kotlin
@Entity
@Table(name = "sys_user")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @Key  // 唯一索引
    val username: String,
    
    @Key(group = "tenant_email")  // 联合唯一索引
    val tenantId: Long,
    
    @Key(group = "tenant_email")
    val email: String,
    
    @ManyToMany
    val roles: List<Role>  // 自动生成中间表
)
```

生成的 DDL：
- `CREATE TABLE sys_user` with columns
- `CREATE UNIQUE INDEX uk_user_username`
- `CREATE UNIQUE INDEX uk_user_tenant_email` (composite)
- `CREATE TABLE role_user_mapping` (junction table)
- Foreign keys for junction table

## SQL 执行

插件使用 `SqlExecutor` 工具类执行 SQL：

```kotlin
SqlExecutor.execute(
    url = "jdbc:mysql://localhost:3306/mydb",
    username = "root",
    password = "password",
    sql = "CREATE TABLE ..."
)
```

连接信息从 Database 插件自动获取。

## 高级功能

### 自定义数据库类型识别
插件会根据数据源 URL 自动识别数据库类型：
- `jdbc:mysql://` → MySQL
- `jdbc:postgresql://` → PostgreSQL
- `jdbc:oracle:` → Oracle

### 差量检测
未来版本将支持：
- 对比现有表结构
- 只生成差异部分（新增列、修改列、新增索引等）
- 智能 ALTER TABLE

## 依赖

### Maven 坐标
```kotlin
implementation("site.addzero:tool-sql-executor:2025.11.26")
```

### 模块依赖
- `lsi-core` - LSI 抽象层
- `lsi-database` - 数据库字段映射
- `lsi-intellij` - IntelliJ 集成
- `tool-ddlgenerator` - DDL 生成器

## 架构设计

```
autoddl-jimmer/
├── settings/              # 配置管理
│   ├── JimmerDdlSettings.kt
│   └── JimmerDdlConfigurable.kt
├── service/               # 核心服务
│   ├── DeltaDdlGenerator.kt
│   └── SqlExecutionService.kt
├── action/                # 用户操作
│   ├── GenerateDeltaDdlAction.kt
│   └── GenerateAndExecuteDdlAction.kt
└── toolwindow/            # 工具窗口
    └── JimmerDdlToolWindowFactory.kt
```

## 故障排查

### 问题：未找到实体类
- 检查扫描包路径配置
- 确保实体类有 `@Entity` 注解

### 问题：SQL 执行失败
- 检查数据源配置
- 确认数据库连接正常
- 检查 SQL 语法（不同数据库可能有差异）

### 问题：找不到数据源
- 在 Database 插件中配置数据源
- 在插件设置中填写正确的数据源名称

## 开发计划

- [ ] 差量检测（对比现有表）
- [ ] 自定义模板
- [ ] 批量执行
- [ ] 执行历史记录
- [ ] 可视化表结构对比

## 贡献

欢迎提交 Issue 和 Pull Request！

## License

MIT License

---

**作者**: zjarlin  
**邮箱**: zjarlin@outlook.com  
**版本**: 1.0.0  
**更新**: 2025-12-07

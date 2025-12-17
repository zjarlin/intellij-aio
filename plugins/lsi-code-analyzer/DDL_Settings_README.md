# DDL 保存功能使用说明

## 功能概述

LSI Code Analyzer 现在支持将生成的 DDL 自动保存到文件，并提供了灵活的配置选项。

## 配置方法

1. 打开 IntelliJ IDEA Settings/Preferences
2. 导航到 Tools -> LSI DDL Settings
3. 配置以下选项：

### 配置项说明

- **DDL 保存目录**: 设置 DDL 文件的保存路径
  - 支持变量：
    - `{projectDir}` - 项目根目录
    - `{entityName}` - 实体名称（取自第一个 POJO 的简单类名）
  - **默认模板**: `{projectDir}/.autoddl/{entityName}`
  - 点击浏览按钮可选择固定目录（不支持变量的目录）

- **自动保存 DDL 到文件**: 启用后，每次生成 DDL 时会自动保存到文件

- **文件名模板**: 自定义 DDL 文件的命名规则（遵循 Flyway 命名规范）
  - 可用变量：
    - `{table}` - 表名
    - `{dialect}` - 数据库方言（如 mysql, postgresql）
    - `{timestamp}` - 时间戳（完整时间戳）
    - `{version}` - 版本号（格式：YYYYMMDDHHMM）
  - 默认模板：`V{timestamp}__Create_{table}_{dialect}.sql`
  - Flyway 命名规范示例：
    - `V202512171430__Create_user_mysql.sql`
    - `V202512171431__AddIndex_user_email_postgresql.sql`
    - `V202512171432__Schema_mysql.sql`

- **生成后打开文件**: 保存成功后自动在编辑器中打开文件

## 使用方法

1. **自动保存**
   - 在 Settings 中启用"自动保存 DDL 到文件"选项
   - 生成 DDL 时会自动保存到配置的路径（默认即可）

2. **手动保存**
   - 生成 DDL 后，点击"保存到文件"按钮
   - 文件会保存到配置的路径

## 文件保存规则

- **单个表**: 使用配置的文件名模板
- **多个表**: 文件名格式为 `schema_{dialect}_{timestamp}.sql`
- **所有表（完整 Schema）**: 文件名格式为 `schema_{dialect}_{timestamp}.sql`

## 示例

假设配置：
- 保存目录：`/Users/username/ddl`
- 文件名模板：`V{version}__Create_{table}_{dialect}.sql`（默认值）

生成 user 表的 MySQL DDL 时，文件会保存为：
`/Users/username/ddl/V202512171430__Create_user_mysql.sql`

## Flyway 命名规范

文件名遵循 Flyway 的命名规范：
- `V` - 版本迁移（Versioned migration）
- `<版本号>` - 基于时间的版本号（YYYYMMDDHHMM）
- `__` - 双下划线分隔符
- `<描述>` - 描述性名称
- `_方言` - 数据库方言标识

这种命名方式使得生成的 DDL 文件可以直接用于 Flyway 数据库版本管理。

## 默认目录结构示例

如果未配置保存目录，系统会自动创建以下目录结构：

```
项目根目录/
├── .autoddl/
│   ├── User/
│   │   ├── V202512171430__Create_user_mysql.sql
│   │   └── V202512171435__AddIndex_user_email_mysql.sql
│   ├── Order/
│   │   ├── V202512171440__Create_order_mysql.sql
│   │   └── V202512171445__Create_order_item_mysql.sql
│   └── Product/
│       ├── V202512171450__Create_product_postgresql.sql
│       └── V202512171455__Schema_postgresql.sql
└── src/
    └── ...
```

## 注意事项

- 确保保存目录有写入权限
- 如果目录不存在，系统会自动创建
- 保存的文件使用 UTF-8 编码
- 文件已存在时会覆盖，请注意备份
- `.autoddl` 目录已加入 `.gitignore` 推荐列表，避免提交 DDL 文件到版本控制
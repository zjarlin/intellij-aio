# 外部SQL执行器集成说明

## 概述

本项目现在集成了两个SQL执行器：

1. **IntelliJ DataBase SQL执行器** ([IntellijDataBaseSqlExecutor](file:///Users/zjarlin/IdeaProjects/autoddl-idea-plugin/lib/tool-database/src/main/kotlin/com/addzero/util/database/IntellijDataBaseSqlExecutor.kt#L22-L247)) - 使用IntelliJ IDEA内置的数据库连接
2. **外部SQL执行器** ([ExternalSqlExecutor](file:///Users/zjarlin/IdeaProjects/autoddl-idea-plugin/lib/tool-database/src/main/kotlin/com/addzero/util/database/ExternalSqlExecutor.kt#L11-L60)) - 使用外部库`site.addzero:tool-sql-executor`直接连接数据库

## IntelliJ DataBase SQL执行器

### 功能特性
- 与IntelliJ IDEA数据库插件集成
- 使用用户在IDE中配置的数据源
- 支持查询、更新、批处理等操作
- 异步执行，支持超时控制
- 自动管理连接

### 使用示例
```kotlin
// 创建执行器
val executor = IntellijDataBaseSqlExecutor.createWithFirst(project)
if (executor == null) {
    // 处理错误
    return
}

// 执行查询
val result = executor.executeQuery("SELECT * FROM users")
if (result.success) {
    result.resultData.forEach { row ->
        println("User: ${row["name"]}")
    }
}
```

## 外部SQL执行器

### 功能特性
- 直接通过JDBC URL连接数据库
- 不依赖IntelliJ IDEA数据库插件配置
- 支持多种数据库（MySQL, PostgreSQL, Oracle, SQL Server）
- 轻量级实现

### 使用示例
```kotlin
// 创建执行器
val executor = ExternalSqlExecutor.create(
    "jdbc:mysql://localhost:3306/mydb",
    "username",
    "password"
)

try {
    // 执行查询
    val results = executor.queryForList("SELECT * FROM users")
    results.forEach { row ->
        println("User: ${row["name"]}")
    }
    
    // 执行更新
    val affectedRows = executor.executeUpdate("UPDATE users SET status = 'active'")
    println("Affected rows: $affectedRows")
} finally {
    executor.close()
}
```

## 依赖配置

项目已添加以下依赖：
```kotlin
implementation("site.addzero:tool-sql-executor:2025.11.26")
```

并配置了本地Maven仓库以解析该依赖：
```kotlin
maven {
    url = uri("file:///Users/zjarlin/IdeaProjects/addzero-lib-jvm/repository")
}
```

## 选择建议

- 如果在IntelliJ IDEA环境中运行，并希望使用用户配置的数据源，推荐使用[IntellijDataBaseSqlExecutor](file:///Users/zjarlin/IdeaProjects/autoddl-idea-plugin/lib/tool-database/src/main/kotlin/com/addzero/util/database/IntellijDataBaseSqlExecutor.kt#L22-L247)
- 如果需要独立于IDE环境直接连接数据库，或者需要连接IDE未配置的数据库，推荐使用[ExternalSqlExecutor](file:///Users/zjarlin/IdeaProjects/autoddl-idea-plugin/lib/tool-database/src/main/kotlin/com/addzero/util/database/ExternalSqlExecutor.kt#L11-L60)
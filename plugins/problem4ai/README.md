# Problem4AI

An IntelliJ IDEA plugin that automatically collects compilation errors and warnings from your project, and generates AI-friendly prompts for quick fixes.

代码问题AI修复助手 - 自动收集项目诊断信息，一键生成AI修复提示词。

## Features / 功能

### 🚀 核心功能

- **饿汉式全局扫描**：项目启动时自动扫描所有文件，建立诊断信息缓存
- **实时更新**：监听代码变化，自动更新缓存
- **便捷扩展函数**：提供 `VirtualFile.problems()` 等扩展函数，快速获取文件问题
- **统计信息**：提供项目级别的错误/警告统计
- **分类显示**：按错误/警告分类显示
- **一键导航**：点击问题跳转到源码位置
- **AI 提示生成**：一键复制生成 AI 修复提示
- **集成到 Problems 面板**：无缝集成到 IDE 原生面板

### 🆕 新特性

#### 1. 饿汉式全局扫描

项目启动后自动进行全量扫描，不再需要打开文件才能收集问题：

```kotlin
// 项目启动时自动触发
GlobalDiagnosticCacheInitializer
```

#### 2. VirtualFile 扩展函数

提供便捷的扩展函数，可以快速获取任何文件的诊断信息。

**支持两种使用方式**：

##### 方式 A：显式传入 Project（推荐）

```kotlin
import site.addzero.diagnostic.extensions.*

// 获取文件的所有问题
val problems: FileDiagnostics? = file.problems(project)

// 获取错误列表
val errors: List<DiagnosticItem> = file.errors(project)

// 获取警告列表  
val warnings: List<DiagnosticItem> = file.warnings(project)

// 检查文件状态
if (file.hasErrors(project)) {
    println("文件有错误")
}

// 获取统计信息
val count: ProblemCount = file.problemCount(project)
println("错误: ${count.errors}, 警告: ${count.warnings}")
```

##### 方式 B：自动推导 Project（便捷）

```kotlin
import site.addzero.diagnostic.extensions.*

// 自动推导 project，无需传参！
val problems: FileDiagnostics? = file.problems()
val errors: List<DiagnosticItem> = file.errors()
val warnings: List<DiagnosticItem> = file.warnings()

// 简洁的检查
if (file.hasErrors()) {
    println("文件有错误")
}

// 一行搞定统计
val count = file.problemCount()
```

**自动推导原理**：
- 通过 `ProjectManager` 查找包含该文件的项目
- 如果文件在多个项目中或无法推导，会抛出 `IllegalStateException`
- 提供 `file.inferProject()` 和 `file.requireProject()` 工具函数

**建议**：在明确知道项目上下文时（如 Action、Service 中），优先使用显式 `project` 参数版本，更明确且性能更好。

#### 3. 全局缓存服务

提供高性能的全局缓存，支持快速查询和统计：

```kotlin
val cache = GlobalDiagnosticCache.getInstance(project)

// 获取所有问题文件
val allProblems = cache.getAllDiagnostics()

// 获取项目统计
val stats = cache.getStatistics()
println("错误文件: ${stats.errorFiles}, 警告文件: ${stats.warningFiles}")

// 监听缓存更新
cache.addListener { event ->
    when (event) {
        is CacheUpdateEvent.FullScan -> println("完成全量扫描")
        is CacheUpdateEvent.IncrementalUpdate -> println("增量更新")
    }
}
```

**面板自动刷新**：
- ✅ 项目启动后自动扫描并显示问题
- ✅ 无需手动点击刷新按钮
- ✅ 缓存更新时自动刷新面板
- ✅ 也可手动点击"刷新"按钮触发重新扫描

## 使用指南

### 基本使用

1. 打开 **Problems** 面板 → **Problem4Ai** Tab
2. 查看问题文件列表
3. 点击复制按钮，粘贴给AI修复

### 高级用法

详细的使用示例请参见 [USAGE_EXAMPLES.md](./USAGE_EXAMPLES.md)

## 支持的文件

Java, Kotlin, Groovy, Scala

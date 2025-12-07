# Problem4AI 使用示例

## 功能概述

Problem4AI 提供了**饿汉式全局扫描**和便捷的扩展函数，让你可以轻松获取项目中任何文件的错误和警告信息。

---

## 1. 使用 VirtualFile 扩展函数

### 1.1 获取文件的所有问题

#### 方式 A：显式传入 Project（推荐 - 明确且安全）

```kotlin
import com.intellij.openapi.vfs.VirtualFile
import site.addzero.diagnostic.extensions.*

fun checkFile(file: VirtualFile, project: Project) {
    // 显式传入 project 参数
    val diagnostics = file.problems(project)
    
    if (diagnostics != null) {
        println("文件 ${file.name} 有 ${diagnostics.items.size} 个问题")
        println("错误数: ${diagnostics.items.count { it.severity == DiagnosticSeverity.ERROR }}")
        println("警告数: ${diagnostics.items.count { it.severity == DiagnosticSeverity.WARNING }}")
    } else {
        println("文件 ${file.name} 没有问题")
    }
}
```

#### 方式 B：自动推导 Project（便捷但有限制）

```kotlin
import site.addzero.diagnostic.extensions.*

fun checkFile(file: VirtualFile) {
    try {
        // 自动推导 project，无需传参
        val diagnostics = file.problems()
        
        if (diagnostics != null) {
            println("文件 ${file.name} 有 ${diagnostics.items.size} 个问题")
        } else {
            println("文件 ${file.name} 没有问题")
        }
    } catch (e: IllegalStateException) {
        println("无法推导项目: ${e.message}")
        // 回退到显式传入 project 的方式
    }
}
```

**自动推导的限制**：
- 文件必须在某个打开的项目的内容根目录下
- 如果文件在多个项目中同时打开，会抛出异常
- 如果无法推导，会抛出 `IllegalStateException`

**建议**：在明确知道项目上下文时，优先使用显式传入 `project` 的版本。

### 1.2 获取错误和警告列表

#### 显式 Project 版本

```kotlin
import site.addzero.diagnostic.extensions.*

fun analyzeFile(file: VirtualFile, project: Project) {
    // 获取所有错误
    val errors = file.errors(project)
    errors.forEach { error ->
        println("错误 [行 ${error.lineNumber}]: ${error.message}")
    }
    
    // 获取所有警告
    val warnings = file.warnings(project)
    warnings.forEach { warning ->
        println("警告 [行 ${warning.lineNumber}]: ${warning.message}")
    }
}
```

#### 自动推导版本

```kotlin
import site.addzero.diagnostic.extensions.*

fun analyzeFile(file: VirtualFile) {
    // 自动推导 project
    val errors = file.errors()
    val warnings = file.warnings()
    
    println("错误: ${errors.size}, 警告: ${warnings.size}")
}
```

### 1.3 快速检查文件状态

#### 显式 Project 版本

```kotlin
import site.addzero.diagnostic.extensions.*

fun quickCheck(file: VirtualFile, project: Project) {
    when {
        file.hasErrors(project) -> println("❌ 文件有错误")
        file.hasWarnings(project) -> println("⚠️ 文件有警告")
        else -> println("✅ 文件没有问题")
    }
}
```

#### 自动推导版本

```kotlin
import site.addzero.diagnostic.extensions.*

fun quickCheck(file: VirtualFile) {
    when {
        file.hasErrors() -> println("❌ 文件有错误")
        file.hasWarnings() -> println("⚠️ 文件有警告")
        else -> println("✅ 文件没有问题")
    }
}
```

### 1.4 获取问题统计

#### 显式 Project 版本

```kotlin
import site.addzero.diagnostic.extensions.*

fun getStatistics(file: VirtualFile, project: Project) {
    val count = file.problemCount(project)
    
    println("问题总数: ${count.total}")
    println("错误: ${count.errors}")
    println("警告: ${count.warnings}")
    
    if (count.hasProblems) {
        println("需要修复！")
    }
}
```

#### 自动推导版本

```kotlin
import site.addzero.diagnostic.extensions.*

fun getStatistics(file: VirtualFile) {
    val count = file.problemCount()
    println("问题: 错误=${count.errors}, 警告=${count.warnings}")
}
```

### 1.5 Project 推导工具函数

```kotlin
import site.addzero.diagnostic.extensions.*

// 尝试推导 project（返回 null 而不抛异常）
val project: Project? = file.inferProject()

// 要求获取 project（失败时抛异常）
try {
    val project: Project = file.requireProject()
    // 使用 project
} catch (e: IllegalStateException) {
    // 无法推导项目
}
```

---

## 2. 使用 GlobalDiagnosticCache 服务

### 2.1 获取全局缓存实例

```kotlin
import site.addzero.diagnostic.service.GlobalDiagnosticCache

fun getCache(project: Project): GlobalDiagnosticCache {
    return GlobalDiagnosticCache.getInstance(project)
}
```

### 2.2 获取项目所有问题

```kotlin
fun getAllProblems(project: Project) {
    val cache = GlobalDiagnosticCache.getInstance(project)
    
    // 获取所有有问题的文件
    val allDiagnostics = cache.getAllDiagnostics()
    println("发现 ${allDiagnostics.size} 个文件有问题")
    
    // 按类型分类
    val errorFiles = cache.getErrorFiles()
    val warningFiles = cache.getWarningFiles()
    
    println("错误文件: ${errorFiles.size}")
    println("警告文件: ${warningFiles.size}")
}
```

### 2.3 获取项目统计信息

```kotlin
fun printProjectStats(project: Project) {
    val cache = GlobalDiagnosticCache.getInstance(project)
    val stats = cache.getStatistics()
    
    println("=== 项目诊断统计 ===")
    println("问题文件总数: ${stats.totalFiles}")
    println("错误文件数: ${stats.errorFiles}")
    println("警告文件数: ${stats.warningFiles}")
    println("错误总数: ${stats.totalErrors}")
    println("警告总数: ${stats.totalWarnings}")
}
```

### 2.4 监听缓存更新

```kotlin
import site.addzero.diagnostic.service.CacheUpdateListener
import site.addzero.diagnostic.service.CacheUpdateEvent

fun setupListener(project: Project) {
    val cache = GlobalDiagnosticCache.getInstance(project)
    
    val listener = CacheUpdateListener { event ->
        when (event) {
            is CacheUpdateEvent.FullScan -> {
                println("完成全量扫描，发现 ${event.diagnostics.size} 个问题文件")
            }
            is CacheUpdateEvent.IncrementalUpdate -> {
                println("增量更新，更新了 ${event.diagnostics.size} 个文件")
            }
        }
    }
    
    cache.addListener(listener)
}
```

### 2.5 触发手动扫描

```kotlin
fun manualScan(project: Project) {
    val cache = GlobalDiagnosticCache.getInstance(project)
    
    // 触发全量扫描
    cache.performFullScan()
    
    println("已触发全量扫描")
}
```

---

## 3. 在 Action 中使用

### 3.1 创建一个显示文件问题的 Action

```kotlin
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import site.addzero.diagnostic.extensions.*

class ShowFileProblemsAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        
        val problems = file.problems(project)
        if (problems == null) {
            Messages.showInfoMessage(
                project,
                "文件没有问题",
                "检查结果"
            )
            return
        }
        
        val message = buildString {
            appendLine("文件: ${file.name}")
            appendLine("问题总数: ${problems.items.size}")
            appendLine()
            problems.items.forEach { item ->
                val type = if (item.severity == DiagnosticSeverity.ERROR) "错误" else "警告"
                appendLine("[$type] 行 ${item.lineNumber}: ${item.message}")
            }
        }
        
        Messages.showInfoMessage(project, message, "文件诊断")
    }
}
```

### 3.2 批量检查多个文件

```kotlin
fun checkMultipleFiles(files: List<VirtualFile>, project: Project) {
    val results = files.map { file ->
        val count = file.problemCount(project)
        Triple(file.name, count.errors, count.warnings)
    }
    
    results.forEach { (name, errors, warnings) ->
        println("$name: $errors 错误, $warnings 警告")
    }
}
```

---

## 4. 在 ToolWindow 中展示

### 4.1 创建问题列表面板

```kotlin
import javax.swing.*
import site.addzero.diagnostic.service.GlobalDiagnosticCache

class ProblemsPanel(private val project: Project) : JPanel() {
    private val listModel = DefaultListModel<String>()
    private val list = JList(listModel)
    
    init {
        layout = BorderLayout()
        add(JScrollPane(list), BorderLayout.CENTER)
        
        // 监听缓存更新
        val cache = GlobalDiagnosticCache.getInstance(project)
        cache.addListener { event ->
            refreshList()
        }
        
        refreshList()
    }
    
    private fun refreshList() {
        listModel.clear()
        
        val cache = GlobalDiagnosticCache.getInstance(project)
        val diagnostics = cache.getAllDiagnostics()
        
        diagnostics.forEach { fileDiag ->
            fileDiag.items.forEach { item ->
                val type = if (item.severity == DiagnosticSeverity.ERROR) "❌" else "⚠️"
                listModel.addElement("$type ${item.file.name}:${item.lineNumber} - ${item.message}")
            }
        }
    }
}
```

---

## 5. 实用工具函数

### 5.1 生成 AI 修复提示

```kotlin
fun generateAiPrompt(file: VirtualFile, project: Project): String? {
    val problems = file.problems(project) ?: return null
    return problems.toAiPrompt()
}
```

### 5.2 过滤特定类型的问题

```kotlin
fun getCompilationErrors(file: VirtualFile, project: Project): List<DiagnosticItem> {
    return file.errors(project).filter { error ->
        error.message.contains("cannot resolve", ignoreCase = true) ||
        error.message.contains("unresolved reference", ignoreCase = true)
    }
}
```

### 5.3 查找问题最多的文件

```kotlin
fun findMostProblematicFiles(project: Project, limit: Int = 10): List<FileDiagnostics> {
    val cache = GlobalDiagnosticCache.getInstance(project)
    return cache.getAllDiagnostics()
        .sortedByDescending { it.items.size }
        .take(limit)
}
```

---

## 6. 性能考虑

### 6.1 检查缓存是否就绪

```kotlin
fun safeGetProblems(file: VirtualFile, project: Project): FileDiagnostics? {
    val cache = GlobalDiagnosticCache.getInstance(project)
    
    if (!cache.isInitialized()) {
        println("缓存尚未初始化，请稍后再试")
        return null
    }
    
    return file.problems(project)
}
```

### 6.3 选择合适的 API

**何时使用显式 Project 参数**：
- ✅ 在 Action、Intention、Inspection 中（已有 project）
- ✅ 在 Service、Component 中（已有 project）
- ✅ 批量处理多个文件时
- ✅ 性能敏感的场景（避免反复推导）

**何时使用自动推导**：
- ✅ 快速原型开发
- ✅ 简单的一次性脚本
- ✅ 确定文件只在一个项目中

```kotlin
// 推荐：显式 project
class MyAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        
        val errors = file.errors(project) // 明确、高效
    }
}

// 可选：自动推导（适合简单场景）
fun quickCheck(file: VirtualFile) {
    if (file.hasErrors()) { // 简洁，但有推导开销
        println("有错误")
    }
}
```

### 6.2 批量操作优化

```kotlin
fun batchCheck(files: List<VirtualFile>, project: Project) {
    val cache = GlobalDiagnosticCache.getInstance(project)
    
    // 一次性获取所有诊断信息
    val allDiagnostics = cache.getAllDiagnostics().associateBy { it.file }
    
    // 高效查询
    files.forEach { file ->
        val diagnostics = allDiagnostics[file]
        // 处理诊断信息
    }
}
```

---

## 总结

Problem4AI 提供了两种主要使用方式：

1. **VirtualFile 扩展函数**：简洁、直观，适合单文件操作
2. **GlobalDiagnosticCache 服务**：强大、灵活，适合全局操作

选择合适的 API 可以让你的代码更加清晰和高效！

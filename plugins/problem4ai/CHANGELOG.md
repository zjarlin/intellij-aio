# Changelog

## [Unreleased] - 2025-12-07

### ✨ Added
- **饿汉式全局扫描**：项目启动时自动扫描所有源文件，建立诊断信息缓存
  - `GlobalDiagnosticCache` 服务：提供全局缓存和快速查询
  - `GlobalDiagnosticCacheInitializer` 启动活动：项目启动时触发扫描
- **VirtualFile 扩展函数**：提供便捷的诊断信息访问接口
  - `VirtualFile.problems(project)`: 获取文件的诊断信息
  - `VirtualFile.errors(project)`: 获取文件的错误列表
  - `VirtualFile.warnings(project)`: 获取文件的警告列表
  - `VirtualFile.hasProblems(project)`: 检查文件是否有问题
  - `VirtualFile.hasErrors(project)`: 检查文件是否有错误
  - `VirtualFile.hasWarnings(project)`: 检查文件是否有警告
  - `VirtualFile.problemCount(project)`: 获取文件的问题统计
- **缓存更新监听**：支持监听缓存更新事件
  - `CacheUpdateListener` 接口
  - `CacheUpdateEvent` 事件类型（FullScan/IncrementalUpdate）
- **项目统计信息**：提供项目级别的诊断统计
  - `DiagnosticStatistics` 数据类
  - 错误文件数、警告文件数、错误总数、警告总数
- **文档完善**：新增 `USAGE_EXAMPLES.md` 详细使用示例

### 🔧 Changed
- 从懒加载模式改为饿汉式全局扫描模式
- 优化诊断信息收集性能
- 改进缓存管理机制

### 📝 Documentation
- 更新 README 添加新功能说明
- 新增完整的使用示例文档

---

## [1.0.0] - 2024-11-26

### Added
- 项目更名为 Problem4AI
- 集成到 IDE Problems 面板，新增 "Problem4Ai" tab
- 自动收集项目编译错误和警告
- 实时监听代码分析事件，自动更新诊断信息
- 一键复制生成AI修复提示词
- 支持 Java, Kotlin, Groovy, Scala 文件

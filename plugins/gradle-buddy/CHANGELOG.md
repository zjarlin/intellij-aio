# Changelog

All notable changes to the Gradle Buddy plugin will be documented in this file.

## [Unreleased] - 2025-12-07

### ✨ Added
- **递归依赖推导**：自动分析并加载模块的所有依赖模块
  - 支持 `project(":path:to:module")` 格式
  - 支持 `projects.path.to.module` Type-safe accessors 格式
  - 使用 BFS 算法避免循环依赖
  - 自动忽略注释掉的依赖声明
- **依赖配置支持**：支持所有 Gradle 依赖配置类型
  - `implementation`, `api`, `compileOnly`, `runtimeOnly`
  - `testImplementation`, `testCompileOnly`, `testRuntimeOnly`
  - `annotationProcessor`, `kapt`, `ksp`
- **Gradle 插件版本更新**：在 settings.gradle.kts 中支持插件版本更新
  - 支持 `id("plugin.id") version "version"` 格式
  - 自动从 Gradle Plugin Portal 查询最新版本
  - 与依赖版本更新使用相同的 Alt+Enter 意图操作
- **字符串工具类**：新增 `StringUtils` 替代 dataframe 依赖
  - `toCamelCaseByDelimiters()`: 转换为 camelCase
  - `toKebabCase()`: 转换为 kebab-case
- **测试覆盖**：新增 `OnDemandModuleLoaderTest` 单元测试
- **文档完善**：新增 `RECURSIVE_DEPENDENCY_DETECTION.md` 详细说明递归依赖推导原理

### 🔧 Changed
- `detectModulesFromOpenFiles()` 现在返回包含递归依赖的完整模块集合
- `OnDemandModuleLoader` 新增私有方法：
  - `expandWithDependencies()`: 递归展开模块及其依赖
  - `extractProjectDependencies()`: 从 build.gradle.kts 提取依赖
  - `findBuildFile()`: 查找模块的构建文件
  - `parseProjectDependencies()`: 解析依赖声明（支持两种格式）

### 🐛 Fixed
- 修复按需加载时可能遗漏传递依赖导致编译失败的问题
- 修复注释掉的依赖被错误解析的问题
- 修复缺失 `org.jetbrains.kotlinx.dataframe` 依赖导致的二进制不兼容问题
  - 移除了对 dataframe 库的依赖
  - 使用自实现的字符串工具函数替代
- 修复 `URL(String)` 废弃 API 使用警告
  - 使用 `URI.toURL()` 替代废弃的构造函数
  - 兼容未来版本的 Java API 变更

### 📝 Documentation
- 更新 README 添加递归依赖推导功能说明
- 新增依赖格式示例和使用场景说明

---

## [2025.11.33] - 2025-11-30

### ✨ Added
- **Auto Sleep 开关**：Module Tasks 面板新增开关，可手动开启/关闭自动睡眠功能
- **智能自动检测**：30+ 模块的大型项目自动开启睡眠，小型项目默认关闭
- **详细 Tooltip**：悬停开关显示模块数量、阈值、当前状态

### 🔧 Changed
- `GradleBuddySettingsService` 新增 `autoSleepEnabled` 设置项
- `GradleBuddyService.isAutoSleepActive()` 方法支持用户设置覆盖自动检测

---

## [2025.11.32] - 2025-11-30

### 🎯 解决的痛点
- **Gradle Sync 慢**：大型多模块项目 Sync 需要 5-10 分钟，现在只需 30 秒
- **内存占用高**：100 个模块全加载占用 8GB+，现在只加载用到的模块
- **手动管理麻烦**：不再需要手动注释 settings.gradle.kts

### ✨ Added
- **StatusBarWidgetFactory**：使用官方稳定 API 注册状态栏组件，替代已废弃的 `addWidget()` 方法
- **模块排除统计**：通知消息显示 `Loaded: X, Excluded: Y, Total: Z`
- **构建模块自动排除**：自动排除 `build-logic`、`buildSrc`、`buildLogic` 等构建基础设施模块
- **settings.gradle.kts 注释增强**：生成的配置块包含统计信息和被排除模块列表

### 🔧 Changed
- **GradleBuddyService 实现 Disposable**：替代实验性的 `ProjectCloseListener`，使用稳定的生命周期管理
- **partitionModules() 函数**：分离有效模块和被排除模块，便于统计和展示

### 🗑️ Removed
- **GradleBuddyProjectManagerListener**：不再需要，由 Disposable 模式替代
- **实验性 API 依赖**：移除 `ProjectCloseListener` 的使用

### 🐛 Fixed
- 修复 `StatusBar.addWidget(StatusBarWidget)` 废弃警告
- 修复 `ProjectCloseListener` 实验性 API 警告

---

## [2025.11.31] - 2025-11-30

### ✨ Added
- **按需模块加载**：只加载当前打开的编辑器标签页对应的模块
- **自动释放机制**：5 分钟未使用的模块自动释放
- **一键加载**：`Ctrl+Alt+Shift+L` 快捷键一键应用按需加载
- **一键恢复**：恢复所有被排除的模块
- **状态栏组件**：显示 Gradle 项目加载状态

### 🛠️ Module Tasks 工具窗口
- 右侧边栏显示当前模块的 Gradle 任务
- 💤 Sleep 按钮：休眠其他模块，只保留当前打开的
- ⏰ Wake 按钮：唤醒所有模块
- 双击任务即可运行

### ✨ 意图操作 (Alt+Enter)
- **Update dependency to latest version**：在依赖声明上按 `Alt+Enter`，自动从 Maven Central 获取最新版本

### 🔄 迁移工具
- **Version Catalog 迁移**：扫描所有 `.gradle.kts` 文件，将硬编码依赖迁移到 `gradle/libs.versions.toml`
- **项目依赖迁移**：将 `project(":module")` 依赖迁移到 Maven 坐标

### 📝 Documentation
- 完整的 README 文档，包含痛点说明和使用方法
- 详细的功能介绍：工具窗口、意图操作、迁移工具
- 代码示例：Version Catalog 迁移前后对比
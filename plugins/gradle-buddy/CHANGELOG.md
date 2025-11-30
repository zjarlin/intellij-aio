# Changelog

All notable changes to the Gradle Buddy plugin will be documented in this file.

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
- **模块任务窗口**：显示当前模块的 Gradle 任务
- **项目依赖迁移**：将 `project(":module")` 依赖迁移到 Maven 依赖
- **Version Catalog 迁移**：将硬编码依赖迁移到 `libs.versions.toml`

### 📝 Documentation
- 完整的 README 文档，包含痛点说明和使用方法
- 前提条件说明：模块独立可运行的要求
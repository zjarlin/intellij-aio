# Changelog

## [1.3.0] - 2024-11-26

### Added
- 集成到 IDE 的 Problems 面板，新增 "AI Fix" tab
- 使用 WolfTheProblemSolver 追踪项目级问题文件
- 自动监听 ProblemListener 事件，实时更新
- 通过 PostStartupActivity 在项目启动时注入到 Problems 面板

### Changed
- 保留独立 Diagnostics 工具窗口作为备选

### Note
- Problems 面板中的 "AI Fix" tab 显示所有被 IDE 标记为有问题的文件
- 双击文件可跳转，工具栏支持刷新和复制功能
- 复制功能生成 AI 修复提示词格式

## [1.2.0] - 2024-11-26 (已废弃)

### Changed
- 使用编译器 API (CompilerTopics) 收集诊断信息
- 编译完成后自动收集所有文件的错误和警告
- 不再依赖 DaemonCodeAnalyzer（只对已打开文件有效）
- 现在可以正确收集整个项目的编译错误

### Note
- 需要先执行 Build 才能收集诊断信息

## [1.1.0] - 2024-11-26

### Changed
- 改为自动收集模式：监听 DaemonCodeAnalyzer 事件，代码分析完成后自动更新
- 添加 1.5 秒防抖机制，避免频繁刷新
- 保留手动刷新按钮，可随时强制刷新

## [1.0.0] - 2024-11-26

### Added
- 初始版本发布
- 自动收集项目编译错误和警告
- Errors/Warnings 分Tab页显示
- 文件级别聚合展示，显示问题数量和行号
- 点击文件名跳转到问题位置
- 单文件复制按钮（位于行左侧）
- 顶部工具栏批量复制功能（全部复制、复制错误、复制警告）
- AI修复提示词格式化输出
- 状态栏显示收集结果统计和复制状态

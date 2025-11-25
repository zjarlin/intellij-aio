# Changelog

All notable changes to the Gradle Favorites plugin will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- ✅ **Keyboard Shortcut Support**
  - Press `Ctrl+Shift+G` (Windows/Linux) or `Cmd+Shift+G` (macOS) to show favorites popup
  - Quick access to favorite tasks without opening tool window
  - Context-aware task filtering based on current file

- ✅ **Task Grouping**
  - Organize tasks into custom groups (e.g., "Build", "Publish", "Test")
  - Group selector when adding new tasks
  - Grouped display in tool window and popup
  - Hierarchical popup navigation for grouped tasks
  - Auto-sorted by group name and order

- ✅ **Search and Filter**
  - Real-time search field in tool window
  - Filter tasks by display name
  - Works seamlessly with grouped display
  - Functional programming style implementation

### Improved
- Enhanced UI panel with search bar at the top
- Better task organization with group headers
- Improved popup navigation with group submenus
- Tasks automatically sorted by group and order
- Cleaner visual separation between groups

## [0.1.0] - 2025-11-23

### Added

#### 核心功能
- ✅ 收藏任务管理系统
  - 手动添加 Gradle 任务到收藏列表
  - 删除不需要的收藏任务
  - 持久化存储到 `.idea/gradleFavorites.xml`

- ✅ 工具窗口 (Tool Window)
  - 右侧边栏 "Gradle Favorites" 面板
  - 可视化管理收藏列表
  - 一键执行收藏的任务
  - 添加/删除/执行 按钮

- ✅ 编辑器上下文菜单集成
  - 右键菜单显示 "Gradle Favorites" 子菜单
  - 自动过滤当前模块的收藏任务
  - 智能识别当前文件所属模块

- ✅ 智能通知系统
  - 打开文件时自动检测收藏任务
  - 非侵入式气泡通知
  - 通知中可直接执行任务
  - 每个模块仅提醒一次(会话级别)

#### 架构设计
- ✅ 策略模式实现上下文处理
  - `GradleToolWindowContextStrategy`: Gradle 面板上下文
  - `EditorContextStrategy`: 编辑器上下文
  - `GradleTaskStrategyRegistry`: 策略注册表

- ✅ 数据持久化
  - 基于 `PersistentStateComponent` 实现
  - 项目级别配置存储
  - 支持版本控制共享

- ✅ 多模块项目支持
  - 自动识别模块路径
  - 支持嵌套模块结构
  - 智能匹配父子模块

#### 文档
- ✅ README.md - 项目说明和快速开始
- ✅ USAGE.md - 详细使用指南
- ✅ STRUCTURE.md - 项目架构文档
- ✅ CHANGELOG.md - 版本更新日志

### Technical Details

- **Platform**: IntelliJ IDEA 2024.2+
- **Language**: Kotlin
- **Build System**: Gradle 8.14
- **Dependencies**: 
  - IntelliJ Platform SDK
  - Gradle Plugin API
  - Kotlin stdlib

### Known Limitations

- ⚠️ Gradle 面板右键菜单集成待完善
  - 当前版本需要手动输入模块路径和任务名
  - 未来版本将支持直接从 Gradle 面板添加

- ⚠️ 任务执行反馈有限
  - 任务执行状态在 Build 工具窗口查看
  - 未来版本将增强执行反馈

### Architecture Highlights

```
分层架构:
- UI Layer: 工具窗口和面板
- Action Layer: 用户操作处理
- Strategy Layer: 上下文策略(无 if-else)
- Service Layer: 业务逻辑和持久化
- Model Layer: 数据模型
```

### Code Metrics

- Kotlin Files: 14
- Lines of Code: ~800
- Packages: 6
- Classes: 14
- Design Patterns: 5 (Strategy, Factory, Observer, Singleton, MVC)

## Roadmap

### [0.2.0] - 计划中

#### 增强功能
- [ ] 从 Gradle 面板直接添加收藏
- [ ] 任务执行历史记录
- [ ] 任务分组功能
- [ ] 自定义任务排序

#### 改进
- [ ] 任务执行进度显示
- [ ] 执行前确认对话框
- [ ] 批量执行任务
- [ ] 快捷键支持

#### 用户体验
- [ ] 拖拽排序
- [ ] 搜索过滤
- [ ] 导入/导出配置
- [ ] 任务标签分类

### [0.3.0] - 计划中

#### 高级功能
- [ ] 任务链执行(先 clean 后 build)
- [ ] 条件执行(仅在文件修改时)
- [ ] 任务参数配置
- [ ] 环境变量支持

#### 团队协作
- [ ] 团队共享模板
- [ ] 任务推荐系统
- [ ] 使用统计分析

## Migration Guide

### 从手动执行到使用插件

**之前:**
```bash
# 频繁在终端执行
./gradlew :lib:tool-psi:kspKotlin
./gradlew :lib:tool-psi:publishToMavenLocal
```

**现在:**
1. 添加到收藏(一次性操作)
2. 在编辑器右键菜单一键执行
3. 收藏列表可与团队共享

## Support

- **Email**: zjarlin@outlook.com
- **Repository**: https://gitee.com/zjarlin/autoddl-idea-plugin

## License

Licensed under the Apache License 2.0

---

**Note**: This is the initial release. Feedback and contributions are welcome!

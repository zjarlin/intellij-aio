# Changelog

## 2026.02.14

### Added
- 初始版本
- UNSAFE_CALL 批量修复：`.` → `?.`
- RETURN_TYPE_MISMATCH 修复：集合类型加 `.orEmpty()`，其他类型加 `?: error("unexpected null")`
- `EditorNotificationProvider` 横幅：有错误时自动显示，修复后自动消失
- `DaemonCodeAnalyzer.DaemonListener` 监听高亮完成，动态刷新横幅
- 右键菜单 + Tools 菜单入口
- 支持中英文错误描述匹配

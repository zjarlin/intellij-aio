# Changelog

## 2026.02.14

### Added
- 初始版本
- 批量调用 IntelliJ 内置 Quick Fix 修复空指针警告
- 智能 Fix 优先级：Surround with null check > Add null check > Replace with > 其他
- `EditorNotificationProvider` 横幅：有空指针警告时自动显示，修复后自动消失
- `DaemonCodeAnalyzer.DaemonListener` 监听高亮完成，动态刷新横幅
- 右键菜单 + Tools 菜单入口
- 支持 WARNING 和 ERROR 两个级别的空指针问题
- JSpecify 注解标注的空安全问题测试数据

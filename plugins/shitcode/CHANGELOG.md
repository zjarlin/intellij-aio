# Changelog

## [Unreleased] - 2025-12-07

### ✨ Features
- **饿汉式扫描**：项目启动时自动扫描所有标记元素
  - 新增 `ShitCodeCacheService` 全局缓存服务
  - 新增 `ShitCodeStartupActivity` 启动活动
  - 工具窗口自动监听缓存更新并刷新
  - 显示统计信息（元素总数、文件数）
  - 无需打开文件即可查看所有标记

### 🐛 Fixed
- **修复刷新扫描问题**：重构注解扫描逻辑，解决无法检测到标记元素的问题
  - 改用 `ktFile.declarations` 遍历，替代 `PsiTreeUtil.processElements`
  - 增强注解匹配逻辑，支持短名称和完全限定名
  - 新增 `hasAnnotation()` 和 `hasJavaAnnotation()` 专用方法
  - 自动过滤构建目录（/build/, /out/, /.gradle/）
  - 正确扫描类内部的方法和字段
- **改进扫描准确性**：
  - Kotlin: 匹配 `shortName`、`fullName` 和 `fullName.endsWith()`
  - Java: 匹配 `shortName`、`qualifiedName` 和 `qualifiedName.endsWith()`

### 📝 Documentation
- 更新 README 添加饿汉式扫描说明
- 更新 README 添加故障排查提示
- 更新 CHANGELOG 记录所有改进

---

## [1.0.0] - Initial Release
- 初始版本发布
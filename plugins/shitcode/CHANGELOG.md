# Changelog

## [Unreleased] - 2025-12-07

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
- 更新 README 添加故障排查提示

---

## [1.0.0] - Initial Release
- 初始版本发布
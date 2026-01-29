# 最新改动记录

## 2026-01-29

### 🐛 Bug 修复

**避免将部分版本目录链误判为未声明**

- **问题**：`libs.gradlePlugin.kotlin` 会被拆成 `libs.gradlePlugin` 导致误报“未声明”
- **原因**：检查时只看当前节点而不是完整 dot 链
- **解决**：跳过部分链节点，并统一从最顶层表达式提取引用
- **影响文件**：`InvalidCatalogReferenceInspection.kt`

## 2025-01-22

### ✨ 新功能

**添加"浏览其他候选项"意图操作**

- **功能**：即使当前引用有效，也可以浏览 TOML 中所有相关的依赖项
- **使用场景**：
  - 当你在 `libs.gradle.plugin.ksp` 上按 `Alt+Enter` 时
  - 即使这个引用是有效的，也会显示所有包含 `gradle`, `plugin`, `ksp` 关键词的候选项
  - 可以快速切换到其他相关的依赖项
- **优势**：
  - 探索 TOML 中的相关依赖
  - 快速切换到其他版本或变体
  - 当前使用的引用会标记为 "✓ 当前"
- **新增文件**：
  - `BrowseCatalogAlternativesIntention.kt`
  - 描述文件和模板

### 🐛 Bug 修复

**修复光标位置影响 token 提取的问题**

- **问题**：当光标在 `libs.bcprov.jdk15to18` 的某个部分（如 `jdk15to18`）时，只会提取部分 token
- **原因**：`PsiTreeUtil.getParentOfType()` 只找到最近的表达式，而不是最顶层的完整表达式
- **解决**：添加向上查找逻辑，确保找到最顶层的 `KtDotQualifiedExpression`
- **影响文件**：`SelectCatalogReferenceIntentionGroup.kt`

### ✨ 功能改进

**移除候选项数量限制**

- **改动前**：只显示 Top 10 候选项
- **改动后**：显示所有有 token 匹配的候选项（无数量限制）
- **优势**：
  - 不会遗漏任何可能的匹配项
  - 按相似度智能排序
  - 用户可以滚动查看所有选项
- **影响文件**：
  - `AliasSimilarityMatcher.kt` - 移除 `topN` 参数
  - `SelectCatalogReferenceIntentionGroup.kt` - 调用时不传递 `topN`

### 📝 文档更新

- 更新 `README.md` - 添加无限制候选列表说明
- 更新 `SIMILARITY_MATCHING_EXAMPLES.md` - 说明无 Top N 限制
- 更新 `BUG_FIX_SUMMARY.md` - 记录两项改动
- 更新 `plugin.xml` - 注册新的意图操作

## 使用示例

### 场景 1：修复无效引用

```kotlin
// 在 build.gradle.kts 中
dependencies {
    implementation(libs.bcprov.jdk15to18)  // 假设这是无效引用
}
```

1. 将光标放在 `bcprov` 或 `jdk15to18` 上
2. 按 `Alt+Enter`
3. 选择 "选择正确的版本目录引用（N 个候选项）"
4. 从弹出菜单中选择正确的引用

### 场景 2：浏览其他候选项

```kotlin
// 在 build.gradle.kts 中
dependencies {
    implementation(libs.gradle.plugin.ksp)  // 这是有效引用
}
```

1. 将光标放在 `gradle`, `plugin`, 或 `ksp` 上
2. 按 `Alt+Enter`
3. 选择 "浏览其他版本目录引用（N 个候选项）"
4. 查看所有包含这些关键词的依赖项
5. 当前使用的引用会标记为 "✓ 当前"
6. 可以选择切换到其他相关依赖

## 测试建议

```kotlin
// 在 build.gradle.kts 中测试
dependencies {
    implementation(libs.bcprov.jdk15to18)  // 假设这是无效引用
    implementation(libs.gradle.plugin.ksp)  // 有效引用
}
```

1. **测试无效引用修复**：
   - 将光标放在 `bcprov` 上，按 `Alt+Enter`
   - 将光标放在 `jdk15to18` 上，按 `Alt+Enter`
   - 验证两个位置显示相同的候选列表
   - 验证显示所有有匹配的候选项（不限制数量）

2. **测试浏览候选项**：
   - 将光标放在 `gradle` 上，按 `Alt+Enter`
   - 将光标放在 `plugin` 上，按 `Alt+Enter`
   - 将光标放在 `ksp` 上，按 `Alt+Enter`
   - 验证都能显示候选项列表
   - 验证当前引用标记为 "✓ 当前"

## Git 提交信息

```bash
git add plugins/gradle-buddy/gradle-buddy-fix-catalog-ref/
git add plugins/gradle-buddy/src/main/resources/META-INF/plugin.xml
git commit -m "feat: 添加浏览候选项功能 & 修复 token 提取 bug & 移除数量限制

1. 新增功能：浏览其他候选项
   - 即使引用有效，也可以查看所有相关的依赖项
   - 使用智能相似度匹配，显示所有包含相同关键词的候选项
   - 当前引用标记为 '✓ 当前'

2. 修复光标位置 bug：
   - 添加向上查找逻辑，确保找到最顶层的表达式
   - 无论光标在哪个位置，都能提取完整的 token 列表

3. 移除 Top N 限制：
   - 显示所有有 token 匹配的候选项
   - 按相似度降序排列
   - 提升用户体验，不遗漏任何可能的匹配

新增文件：
- BrowseCatalogAlternativesIntention.kt
- intentionDescriptions/BrowseCatalogAlternativesIntention/*

修改文件：
- SelectCatalogReferenceIntentionGroup.kt
- AliasSimilarityMatcher.kt
- plugin.xml
- README.md
- SIMILARITY_MATCHING_EXAMPLES.md
- BUG_FIX_SUMMARY.md
- CHANGELOG_LATEST.md"
```

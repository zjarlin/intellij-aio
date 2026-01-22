# Bug Fix: Token Extraction Issue

## 问题描述

当光标位于 `libs.bcprov.jdk15to18` 表达式的某个部分（如 `jdk15to18`）时，插件无法提取完整的 token 列表进行相似度匹配。

### 原因分析

`PsiTreeUtil.getParentOfType()` 只会找到包含当前元素的**最近的** `KtDotQualifiedExpression`，而不是最顶层的完整表达式。

例如：
- 表达式：`libs.bcprov.jdk15to18`
- 光标在 `jdk15to18` 上
- 旧逻辑找到：`jdk15to18`（部分表达式）
- 期望找到：`libs.bcprov.jdk15to18`（完整表达式）

这导致只提取了 `[jdk15to18]` 这一个 token，而不是 `[bcprov, jdk15to18]`。

## 解决方案

在 `SelectCatalogReferenceIntentionGroup.detectCatalogReferenceError()` 方法中添加向上查找逻辑：

```kotlin
private fun detectCatalogReferenceError(project: Project, element: PsiElement): CatalogReferenceError? {
    // 找到最顶层的 KtDotQualifiedExpression
    var dotExpression = PsiTreeUtil.getParentOfType(element, KtDotQualifiedExpression::class.java)
        ?: return null

    // 向上查找，直到找到最顶层的 KtDotQualifiedExpression
    while (dotExpression.parent is KtDotQualifiedExpression) {
        dotExpression = dotExpression.parent as KtDotQualifiedExpression
    }

    // ... 后续逻辑
}
```

## 修复效果

修复后，无论光标在表达式的哪个位置：

| 表达式 | 光标位置 | 提取的 tokens |
|--------|---------|--------------|
| `libs.bcprov.jdk15to18` | `bcprov` | `[bcprov, jdk15to18]` ✅ |
| `libs.bcprov.jdk15to18` | `jdk15to18` | `[bcprov, jdk15to18]` ✅ |
| `libs.com.google.devtools.ksp` | `google` | `[com, google, devtools, ksp]` ✅ |
| `libs.com.google.devtools.ksp` | `ksp` | `[com, google, devtools, ksp]` ✅ |

---

# Feature Update: 移除 Top N 限制

## 改动说明

移除了候选项数量限制（原来是 Top 10），现在显示**所有**有 token 匹配的候选项。

### 修改内容

1. **AliasSimilarityMatcher.kt**
   - 移除 `topN` 参数
   - 移除 `.take(topN)` 调用
   - 返回所有 score > 0 的候选项

2. **SelectCatalogReferenceIntentionGroup.kt**
   - 调用 `findSimilarAliases()` 时不再传递 `topN` 参数
   - 候选项数量显示为实际匹配数量

### 用户体验改进

- **更全面的候选项**：不会遗漏任何可能的匹配项
- **智能排序**：按相似度降序排列，最相关的在最前面
- **可滚动菜单**：如果候选项很多，用户可以滚动查看所有选项

### 示例

假设 TOML 中有 50 个包含 `gradle` 关键词的依赖：

**旧行为**：只显示前 10 个
**新行为**：显示所有 50 个，按相似度排序

## 测试建议

1. 在 `build.gradle.kts` 中添加测试代码：
   ```kotlin
   dependencies {
       implementation(libs.bcprov.jdk15to18)  // 假设这是无效引用
   }
   ```

2. 将光标分别放在 `bcprov` 和 `jdk15to18` 上

3. 按 `Alt+Enter`，验证：
   - 两个位置都能触发意图操作
   - 候选列表相同
   - 相似度匹配使用了所有 token
   - 显示所有有匹配的候选项（不限制数量）

## 相关文件

- `SelectCatalogReferenceIntentionGroup.kt` - 已修复 ✅
- `AliasSimilarityMatcher.kt` - 已移除 topN 限制 ✅
- `InvalidCatalogReferenceInspection.kt` - 无需修复（使用 Visitor 模式，自动访问顶层节点）

## 提交信息

```
fix: 修复光标位置影响 token 提取的问题 & 移除候选项数量限制

1. 当光标在 libs.bcprov.jdk15to18 的任意部分时，现在都能正确提取所有 token 进行相似度匹配
2. 移除 Top N 限制，显示所有有 token 匹配的候选项，按相似度排序

修改：
- SelectCatalogReferenceIntentionGroup: 添加向上查找逻辑，确保找到最顶层的表达式
- AliasSimilarityMatcher: 移除 topN 参数，返回所有匹配的候选项
```

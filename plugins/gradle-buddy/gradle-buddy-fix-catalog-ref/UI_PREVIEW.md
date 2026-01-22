# UI 预览：智能建议上下文菜单

## 新的用户体验

我们已经将对话框改为更智能的上下文菜单形式，提供更好的用户体验和预览效果。

### 旧的方式（对话框）

```
❌ 弹出对话框
┌─────────────────────────────────────────┐
│ 选择版本目录引用                          │
├─────────────────────────────────────────┤
│ ○ gradle.plugin.ksp (60%, 匹配: ...)    │
│ ○ ksp.symbol.processing.api (12%, ...) │
│ ○ ...                                   │
├─────────────────────────────────────────┤
│           [确定]    [取消]               │
└─────────────────────────────────────────┘
```

**缺点**：
- 模态对话框，阻塞操作
- 无法预览替换效果
- 需要额外点击确定按钮
- 不符合 IntelliJ 的 UI 风格

### 新的方式（智能上下文菜单）

```
implementation(libs.kotlin.gradle.plugin)
                    ↑ 光标在这里

按 Alt+Enter 显示：

┌─────────────────────────────────────────────────────────┐
│ ⚡ 选择正确的版本目录引用（10 个候选项）                  │
├─────────────────────────────────────────────────────────┤
│ ▶ 选择版本目录引用                                       │
│   ├─ libs.gradlePlugin.kotlin [85%] (匹配: gradle, ...) │
│   ├─ libs.gradle.plugin.ksp [60%] (匹配: gradle, ...)   │
│   ├─ libs.kotlin.gradle.plugin.api [45%] (匹配: ...)    │
│   ├─ libs.ksp.symbol.processing.api [30%] (匹配: ksp)   │
│   ├─ ...                                                 │
│   └─ libs.kotlin.stdlib [15%] (匹配: kotlin)            │
└─────────────────────────────────────────────────────────┘
```

**优点**：
- ✅ 非模态，不阻塞操作
- ✅ 可以使用键盘快速选择（↑↓ + Enter）
- ✅ 显示匹配度和匹配的词，帮助用户判断
- ✅ 符合 IntelliJ 的 UI 风格
- ✅ 可以按 Esc 取消，不影响代码
- ✅ 支持搜索过滤（输入字符快速定位）

## 实际使用流程

### 场景 1：有多个候选项

1. **检测到错误**：
   ```kotlin
   implementation(libs.kotlin.gradle.plugin)
                       ~~~~~~~~~~~~~~~~~~~~~~ 黄色波浪线
   ```

2. **按 Alt+Enter**：
   ```
   ┌─────────────────────────────────────────────────────────┐
   │ ⚡ 选择正确的版本目录引用（10 个候选项）                  │
   ├─────────────────────────────────────────────────────────┤
   │ ▶ 选择版本目录引用                                       │
   │   ├─ libs.gradlePlugin.kotlin [85%] (匹配: gradle, ...) │ ← 高匹配度
   │   ├─ libs.gradle.plugin.ksp [60%] (匹配: gradle, ...)   │
   │   ├─ libs.kotlin.gradle.plugin.api [45%] (匹配: ...)    │
   │   └─ ...                                                 │
   └─────────────────────────────────────────────────────────┘
   ```

3. **选择候选项**：
   - 使用 ↑↓ 键浏览
   - 或者直接输入字符过滤（如输入 "gradle" 只显示包含 gradle 的项）
   - 按 Enter 确认

4. **自动替换**：
   ```kotlin
   implementation(libs.gradlePlugin.kotlin)
   ```

### 场景 2：没有候选项

1. **检测到错误**：
   ```kotlin
   implementation(libs.completely.unknown.library)
                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~ 黄色波浪线
   ```

2. **按 Alt+Enter**：
   ```
   ┌─────────────────────────────────────────────────────────┐
   │ ⚠ 版本目录中未声明 'completely.unknown.library'          │
   │                                                          │
   │ 也没有找到相似的别名。                                    │
   │                                                          │
   │ 请在 TOML 文件中添加对应的声明，例如：                    │
   │                                                          │
   │ [libraries]                                              │
   │ completely-unknown-library = { ... }                     │
   └─────────────────────────────────────────────────────────┘
   ```

## 技术实现

### 核心组件

1. **SelectCatalogReferenceIntentionGroup**：
   - 主意图操作
   - 检测错误并缓存候选项
   - 显示智能弹出菜单

2. **JBPopupFactory**：
   - IntelliJ 的标准弹出菜单工厂
   - 提供键盘导航、搜索过滤等功能
   - 自动处理位置和大小

3. **BaseListPopupStep**：
   - 列表弹出菜单的基类
   - 自定义显示文本和选择行为

### 优先级系统

候选项根据匹配度自动排序：
- **≥70%**：高优先级（显示在最前面）
- **40-69%**：普通优先级
- **<40%**：低优先级（显示在最后面）

### 显示格式

```
libs.gradlePlugin.kotlin [85%] (匹配: gradle, kotlin, plugin)
└─┬─┘ └────────┬────────┘ └┬┘  └──────────┬──────────────┘
  │            │            │              │
  │            │            │              └─ 匹配的词（最多显示3个）
  │            │            └─ 匹配度百分比
  │            └─ 建议的别名
  └─ 目录名
```

## 与其他 IntelliJ 功能的一致性

这种 UI 风格与 IntelliJ 的其他功能保持一致：

1. **Import 建议**：
   ```
   Alt+Enter on unresolved reference
   ├─ Import 'com.example.MyClass'
   ├─ Import 'com.other.MyClass'
   └─ ...
   ```

2. **重构建议**：
   ```
   Alt+Enter on variable
   ├─ Rename...
   ├─ Change type to 'String'
   ├─ Introduce variable
   └─ ...
   ```

3. **快速修复**：
   ```
   Alt+Enter on error
   ├─ Add missing import
   ├─ Create function 'foo'
   └─ ...
   ```

我们的版本目录引用修复也遵循同样的模式，提供一致的用户体验。

## 键盘快捷键

- **Alt+Enter**：显示意图菜单
- **↑/↓**：浏览候选项
- **Enter**：选择当前项
- **Esc**：取消
- **输入字符**：过滤候选项（如输入 "gradle" 只显示包含 gradle 的项）
- **PgUp/PgDn**：快速翻页

## 预览效果

用户可以在选择前看到：
1. **完整的新引用**：`libs.gradlePlugin.kotlin`
2. **匹配度**：`[85%]`
3. **匹配的词**：`(匹配: gradle, kotlin, plugin)`

这些信息帮助用户快速判断哪个候选项是正确的。

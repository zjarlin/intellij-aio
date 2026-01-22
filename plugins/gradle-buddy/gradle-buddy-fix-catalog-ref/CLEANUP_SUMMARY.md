# 代码清理总结

## 删除的文件

1. **`FixCatalogReferenceIntention.kt`** ❌
   - 旧的对话框形式的意图
   - 已被 `SelectCatalogReferenceIntentionGroup` 替代

2. **`SelectCatalogReferenceIntention.kt`** ❌
   - 单个候选项的意图（未使用）
   - 功能已集成到 `SelectCatalogReferenceIntentionGroup` 中

## 简化的文件

1. **`NotDeclaredFixStrategy.kt`** ✨
   - 移除了对话框相关的代码
   - 只保留"添加到 TOML"的提示功能
   - 有候选项的情况由 `SelectCatalogReferenceIntentionGroup` 处理

2. **`plugin.xml`** ✨
   - 移除了旧的 `FixCatalogReferenceIntention` 注册
   - 只保留 `SelectCatalogReferenceIntentionGroup` 注册

## 保留的核心文件

### 主要功能

1. **`SelectCatalogReferenceIntentionGroup.kt`** ✅
   - 唯一的意图入口
   - 显示智能上下文菜单
   - 处理所有候选项的选择

2. **`CatalogReferenceScanner.kt`** ✅
   - 扫描 TOML 文件
   - 提取所有别名

3. **`AliasSimilarityMatcher.kt`** ✅
   - 计算相似度
   - 返回 Top 10 候选项

### 策略模式

4. **`CatalogFixStrategy.kt`** ✅
   - 策略接口

5. **`WrongFormatFixStrategy.kt`** ✅
   - 处理格式错误（简单替换）

6. **`NotDeclaredFixStrategy.kt`** ✅
   - 处理未声明错误（只处理无候选项的情况）

7. **`CatalogFixStrategyFactory.kt`** ✅
   - 策略工厂

### 数据模型

8. **`CatalogReferenceError.kt`** ✅
   - 错误类型定义
   - `WrongFormat` 和 `NotDeclared`

### 检查

9. **`InvalidCatalogReferenceInspection.kt`** ✅
   - 自动检测错误
   - 显示黄色波浪线

## 最终架构

```
用户按 Alt+Enter
    ↓
SelectCatalogReferenceIntentionGroup
    ↓
检测错误类型
    ↓
┌─────────────────┬─────────────────┐
│ WrongFormat     │ NotDeclared     │
│ (格式错误)       │ (未声明)         │
└─────────────────┴─────────────────┘
         ↓                  ↓
  WrongFormat        有候选项？
  FixStrategy            ↓
         ↓          ┌────┴────┐
    直接替换        是         否
                   ↓          ↓
            显示上下文菜单  提示添加到TOML
            (Top 10)      (Messages.show)
```

## 用户体验

### 场景 1：格式错误（WrongFormat）

```kotlin
implementation(libs.gradlePlugin.ksp)
                    ~~~~~~~~~~~~~~~~~~~ 黄色波浪线
```

按 Alt+Enter → 直接替换为 `libs.gradle.plugin.ksp`

### 场景 2：未声明 + 有候选项（NotDeclared with candidates）

```kotlin
implementation(libs.kotlin.gradle.plugin)
                    ~~~~~~~~~~~~~~~~~~~~~~ 黄色波浪线
```

按 Alt+Enter → 显示上下文菜单：

```
┌─────────────────────────────────────────────────────────┐
│ ⚡ 选择正确的版本目录引用（10 个候选项）                  │
├─────────────────────────────────────────────────────────┤
│ libs.gradlePlugin.kotlin [85%] (匹配: gradle, kotlin)   │
│ libs.gradle.plugin.ksp [60%] (匹配: gradle, plugin)     │
│ libs.kotlin.gradle.plugin.api [45%] (匹配: kotlin, ...) │
│ ...                                                      │
└─────────────────────────────────────────────────────────┘
```

### 场景 3：未声明 + 无候选项（NotDeclared without candidates）

```kotlin
implementation(libs.completely.unknown.library)
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~ 黄色波浪线
```

按 Alt+Enter → 显示提示对话框：

```
┌─────────────────────────────────────────────────────────┐
│ ⚠ 版本目录中未找到 'completely.unknown.library'          │
│                                                          │
│ 也没有找到相似的别名。                                    │
│                                                          │
│ 请在 TOML 文件中添加对应的声明...                         │
└─────────────────────────────────────────────────────────┘
```

## 代码统计

### 删除

- 2 个文件
- 约 400 行代码

### 简化

- 2 个文件
- 减少约 150 行代码

### 保留

- 9 个核心文件
- 约 1200 行代码

## 优势

1. **更简洁**：只有一个意图入口，不会出现重复
2. **更智能**：上下文菜单比对话框更符合 IDE 习惯
3. **更快速**：键盘导航，无需鼠标点击
4. **更清晰**：代码职责分明，易于维护

## 测试清单

- [ ] WrongFormat 错误能正确替换
- [ ] NotDeclared 有候选项时显示上下文菜单
- [ ] NotDeclared 无候选项时显示提示对话框
- [ ] 上下文菜单支持键盘导航
- [ ] 上下文菜单支持搜索过滤
- [ ] 匹配度和匹配词正确显示
- [ ] 替换后代码正确

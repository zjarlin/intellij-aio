# Maven Buddy - 更新日志

## [Unreleased] - 2025-11-26

### ✨ 新增功能

#### 1. 搜索历史与缓存
- 📜 **搜索历史**: 记录使用过的依赖，搜索框为空时下拉显示（最多 30 条）
- 💾 **持久化缓存**: 搜索结果缓存 7 天，避免重复调用 API（最多 200 条）
- 📊 **分组显示**: 历史(📜橙色)、缓存(💾蓝色)、搜索(🔍绿色) 三种来源明确区分
- ⏱️ **时间排序**: 搜索结果按 timestamp 降序排列（最新的在前）

#### 2. 翻页支持
- 📄 **分页加载**: 默认每页 50 条结果
- 🔄 **滚动加载**: 滚动到底部自动加载下一页
- 📊 **进度显示**: `🔍 50 / 1234 ↓ scroll for more`

#### 3. Version Catalog 支持
- 📝 **TOML 补全**: 在 `*.versions.toml` 文件中智能补全依赖
  - 支持简写形式: `guava = "com.google.guava:guava:32.1.3-jre"`
  - 支持 module 形式: `{ module = "g:a", version = "v" }`
  - 支持完整形式: `{ group = "g", name = "a", version = "v" }`
- 🔄 **批量迁移**: `Tools → Migrate Dependencies to Version Catalog`
  - 扫描项目中所有 `.gradle.kts` 文件
  - 提取硬编码依赖 `implementation("g:a:v")`
  - 生成/合并 `gradle/libs.versions.toml`
  - 替换为 `implementation(libs.xxx)` 格式
  - toml 横杠自动转换为 kts 中的点号 (`kotlin-reflect` → `libs.kotlin.reflect`)

### 🔧 技术改进

#### 缓存服务
```kotlin
// SearchResultCacheService.kt
- 持久化到 MavenSearchResultCache.xml
- 按关键词缓存搜索结果
- 自动过期清理（7 天）
- 最大缓存 200 条
```

#### 分组渲染
```kotlin
// MavenArtifactCellRenderer.kt
- repositoryId = "history" → 📜 橙色背景
- repositoryId = "cached" → 💾 蓝色背景
- 其他 → 🔍 默认背景
```

---

## [Previous] - 2025-11-23

### ✨ 新增功能

#### 1. 统一搜索策略 - `searchByKeyword` 优先级最高
- 🎯 所有搜索请求统一使用 `MavenCentralSearchUtil.searchByKeyword(pattern, maxResults)`
- ✅ 支持所有搜索模式：关键词、GroupId、GroupId:ArtifactId、完整坐标
- 📝 简化代码逻辑，移除复杂的条件判断
- 🔍 详见: [SEARCH_STRATEGY.md](SEARCH_STRATEGY.md)

#### 2. 防抖（Debounce）配置
- ⏱️ 新增防抖延迟配置项
- 📊 默认值: 500 毫秒
- 🎚️ 可调范围: 100-2000 毫秒
- 💡 推荐配置:
  - 300ms - 快速响应
  - 500ms - 平衡选项（默认）
  - 800ms - 慢速网络
- 🚀 避免输入过程中的频繁网络请求
- 🔧 自动取消过时的搜索任务

#### 3. 手动触发搜索模式
- 🔑 新增 "Require Enter key to trigger search" 选项
- 🎮 启用后必须按 Enter 键才触发搜索
- 💻 适用场景:
  - 完全控制搜索时机
  - 避免输入过程中的网络请求
  - 网络环境不稳定
- ⚙️ 手动模式下自动禁用防抖延迟配置

### 🔧 技术改进

#### 搜索逻辑优化
```kotlin
// 之前：复杂的条件判断
if (pattern.contains(':')) {
    when (parts.size) {
        1 -> searchByGroupId(...)
        2 -> searchByCoordinates(...)
        else -> searchByKeyword(...)
    }
} else {
    searchByKeyword(...)
}

// 现在：统一简洁
MavenCentralSearchUtil.searchByKeyword(pattern, maxResults)
```

#### 防抖实现
- 使用 `AtomicReference<ScheduledFuture<*>?>` 管理搜索任务
- 使用 `AppExecutorUtil.getAppScheduledExecutorService()` 调度延迟任务
- 自动取消被替代的搜索任务
- 确保只显示最新的搜索结果

### 📝 文档更新

#### 新增文档
- ✅ [SEARCH_STRATEGY.md](SEARCH_STRATEGY.md) - 搜索策略详细说明
- ✅ [DEBOUNCE_CONFIG.md](DEBOUNCE_CONFIG.md) - 防抖配置和使用指南

#### 更新文档
- ✅ [README.md](README.md) - 添加搜索行为配置说明
- ✅ [INTEGRATION.md](INTEGRATION.md) - 更新搜索逻辑示例

### 🎨 UI 改进

#### 设置页面
- ➕ 添加 "Search Behavior" 分组
- 🎚️ 添加防抖延迟输入框
- ☑️ 添加手动触发复选框
- 💡 添加详细的工具提示（Tooltip）
- 🔗 防抖延迟在手动模式下自动禁用

### ⚡ 性能优化

#### 搜索性能
- 🚀 减少不必要的网络请求（防抖机制）
- 🔄 自动取消过时的搜索任务
- 📊 只处理最新的搜索结果
- 💾 节省网络带宽和 CPU 资源

#### 内存优化
- 使用 `@Volatile` 确保多线程安全
- 使用 `AtomicReference` 避免竞态条件
- 正确处理 `ProgressIndicator.isCanceled`

### 🐛 修复

- ✅ 修复输入过程中的立即搜索问题
- ✅ 修复多个搜索任务同时运行导致的结果覆盖
- ✅ 修复快速输入时的网络请求风暴

### 📊 配置变更

#### 新增配置项
```kotlin
// MavenSearchSettings.kt
var debounceDelay: Int = 500              // 防抖延迟（毫秒）
var requireManualTrigger: Boolean = false  // 是否需要手动触发
```

#### 默认配置
```
依赖格式: Gradle Kotlin DSL
最大结果数: 20
自动复制: 启用
搜索超时: 10 秒
防抖延迟: 500 毫秒
手动触发: 禁用
```

### 💡 使用建议

#### 推荐配置

**日常开发（默认）**:
```
✅ 自动搜索（不勾选手动触发）
⏱️ 防抖延迟: 500ms
```

**网络受限环境**:
```
✅ 自动搜索（不勾选手动触发）
⏱️ 防抖延迟: 800ms
```

**精确搜索场景**:
```
☑️ 勾选 "Require Enter key to trigger search"
```

### 🔍 工作原理

#### 自动搜索模式（防抖）
```
用户输入: j a c k s o n
防抖延迟: 500ms

时间轴:
0ms:    输入 'j'        → 启动 500ms 定时器
100ms:  输入 'a'        → 取消上次定时器，重新启动
...
600ms:  输入 'n'        → 取消上次定时器，重新启动
700ms:  停止输入
1200ms: 触发搜索 "jackson" ✅
```

#### 手动触发模式
```
用户输入: jackson
按 Enter 键: 触发搜索 ✅
```

### 📚 相关资源

- [SEARCH_STRATEGY.md](SEARCH_STRATEGY.md) - 搜索策略说明
- [DEBOUNCE_CONFIG.md](DEBOUNCE_CONFIG.md) - 防抖配置详解
- [INTEGRATION.md](INTEGRATION.md) - 集成文档
- [USAGE.md](USAGE.md) - 使用说明
- [README.md](README.md) - 项目主页

---

## 总结

本次更新主要优化了搜索体验：

1. ✅ **统一搜索策略** - 使用 `searchByKeyword` 作为唯一搜索方法
2. ⚡ **防抖机制** - 避免输入过程中的频繁请求
3. 🎮 **手动触发模式** - 给用户完全的控制权
4. 📝 **完善文档** - 详细的配置和使用说明
5. 🚀 **性能提升** - 减少不必要的网络请求和资源消耗

**让 Maven 依赖搜索更智能、更高效！** 🎉
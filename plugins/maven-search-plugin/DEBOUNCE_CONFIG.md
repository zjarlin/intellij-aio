# 防抖配置和手动触发说明

## 🎯 功能概述

Maven 搜索插件提供了两种搜索触发模式：

1. **自动搜索模式（默认）** - 输入停止后自动触发搜索（使用防抖）
2. **手动触发模式** - 需要按 Enter 键才触发搜索

## ⚙️ 配置位置

**Settings → Tools → Maven Search → Search Behavior**

## 🔧 配置选项

### 1. Debounce Delay（防抖延迟）

**作用**: 在自动搜索模式下，用户停止输入后等待多久才触发搜索

**默认值**: 500 毫秒

**推荐值**:
- **300ms** - 快速响应，适合快速输入的用户
- **500ms** - 平衡选项（推荐），适合大多数场景
- **800ms** - 减少请求，适合网络较慢的环境

**范围**: 100-2000 毫秒

**示例**:
```
用户输入: j a c k s o n
防抖延迟: 500ms

时间轴:
0ms:    输入 'j'        → 启动 500ms 定时器
100ms:  输入 'a'        → 取消上次定时器，重新启动 500ms 定时器
200ms:  输入 'c'        → 取消上次定时器，重新启动 500ms 定时器
300ms:  输入 'k'        → 取消上次定时器，重新启动 500ms 定时器
400ms:  输入 's'        → 取消上次定时器，重新启动 500ms 定时器
500ms:  输入 'o'        → 取消上次定时器，重新启动 500ms 定时器
600ms:  输入 'n'        → 取消上次定时器，重新启动 500ms 定时器
700ms:  停止输入
1200ms: 触发搜索 "jackson" ✅
```

### 2. Require Enter Key to Trigger Search（手动触发模式）

**作用**: 禁用自动搜索，只有用户按下 Enter 键时才触发搜索

**默认值**: 关闭（false）

**适用场景**:
- 你想完全控制何时触发搜索
- 避免输入过程中的网络请求
- 网络环境不稳定，想减少不必要的请求

**启用后**:
- 防抖延迟选项会被禁用（变灰）
- 用户必须按 Enter 键才会触发搜索
- 内部使用较长的延迟（1000ms）来避免误触发

## 📊 模式对比

| 特性 | 自动搜索模式 | 手动触发模式 |
|-----|------------|------------|
| 触发方式 | 输入停止后自动触发 | 按 Enter 键触发 |
| 防抖延迟 | 可配置（300-800ms） | 固定 1000ms |
| 网络请求 | 频繁（每次输入停止） | 较少（仅按 Enter） |
| 用户体验 | 快速响应 | 需要额外操作 |
| 适用场景 | 快速查找、实时反馈 | 精确搜索、网络受限 |

## 🎮 使用示例

### 场景 1: 快速查找（推荐自动模式）

**配置**:
- ☑️ 自动搜索（不勾选手动触发）
- 防抖延迟: 500ms

**使用流程**:
1. 双击 Shift 打开 Search Everywhere
2. 切换到 "Maven Dependencies" 标签
3. 输入 "jackson"
4. 停止输入 500ms 后，自动显示搜索结果 ✅
5. 选择需要的依赖，按 Enter 复制到剪贴板

### 场景 2: 精确搜索（推荐手动模式）

**配置**:
- ☑️ 勾选 "Require Enter key to trigger search"

**使用流程**:
1. 双击 Shift 打开 Search Everywhere
2. 切换到 "Maven Dependencies" 标签
3. 输入 "com.google.inject:guice"
4. 按 Enter 键触发搜索 ✅
5. 显示搜索结果
6. 选择需要的依赖，按 Enter 复制到剪贴板

### 场景 3: 慢速网络环境

**配置**:
- ☑️ 自动搜索（不勾选手动触发）
- 防抖延迟: 800ms

**优势**:
- 减少频繁的网络请求
- 给用户更多时间完成输入
- 避免网络拥堵

## 🚀 性能优化

### 防抖的好处

**问题**: 没有防抖时，每次输入都会触发搜索
```
输入: j → 搜索 "j"      (请求 1)
输入: a → 搜索 "ja"     (请求 2)
输入: c → 搜索 "jac"    (请求 3)
输入: k → 搜索 "jack"   (请求 4)
...
结果: 大量无用的网络请求，浪费带宽
```

**解决**: 使用防抖，只在输入停止后触发一次搜索
```
输入: j a c k s o n → 停止 → 搜索 "jackson" (请求 1)
结果: 只有 1 次网络请求，高效！
```

### 请求取消机制

插件内部使用 `AtomicReference<ScheduledFuture<*>?>` 来管理搜索任务：

```kotlin
// 用户输入 "ja"
scheduledFuture.set(schedule("ja", 500ms))

// 用户继续输入 "jack"（还没到 500ms）
scheduledFuture.get()?.cancel(false)  // ❌ 取消 "ja" 的搜索
scheduledFuture.set(schedule("jack", 500ms))  // ✅ 启动 "jack" 的搜索
```

**好处**:
- 避免过时的搜索结果干扰
- 只显示最新的搜索结果
- 节省网络和 CPU 资源

## 🔍 技术实现

### 自动搜索模式

```kotlin
override fun fetchElements(pattern: String, ...) {
    if (!settings.requireManualTrigger) {
        // 使用配置的防抖延迟
        performDebouncedSearch(pattern, ..., settings.debounceDelay.toLong())
    }
}
```

### 手动触发模式

```kotlin
override fun fetchElements(pattern: String, ...) {
    if (settings.requireManualTrigger) {
        // 使用较长的延迟（1000ms）来避免误触发
        performDebouncedSearch(pattern, ..., 1000)
    }
}
```

### 防抖核心逻辑

```kotlin
private fun performDebouncedSearch(pattern: String, delayMs: Long) {
    // 1. 取消之前的搜索任务
    scheduledFuture.get()?.cancel(false)
    
    // 2. 更新最后搜索关键词
    lastSearchPattern = pattern
    
    // 3. 调度新的搜索任务
    val future = AppExecutorUtil.getAppScheduledExecutorService().schedule({
        // 4. 确保没有被新的搜索替代
        if (lastSearchPattern == pattern && !progressIndicator.isCanceled) {
            // 5. 执行实际搜索
            val results = searchMavenArtifacts(pattern, progressIndicator)
            // 6. 处理结果
            for (artifact in results) {
                consumer.process(artifact)
            }
        }
    }, delayMs, TimeUnit.MILLISECONDS)
    
    scheduledFuture.set(future)
}
```

## 📝 最佳实践

### 推荐配置

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

### 调优建议

1. **快速输入用户** → 降低防抖延迟（300ms）
2. **慢速输入用户** → 提高防抖延迟（800ms）
3. **移动热点/VPN** → 使用手动触发模式
4. **本地网络** → 使用自动搜索模式（500ms）

## 🐛 故障排查

### 问题 1: 搜索触发太快/太慢

**解决**: 调整防抖延迟
- 太快 → 增加延迟（如 800ms）
- 太慢 → 减少延迟（如 300ms）

### 问题 2: 搜索结果被覆盖

**原因**: 防抖延迟太短，导致多个搜索同时触发

**解决**: 增加防抖延迟（推荐 500ms 或更高）

### 问题 3: 手动模式下仍然自动搜索

**原因**: 设置未保存

**解决**: 
1. 检查设置页面
2. 确保勾选了 "Require Enter key to trigger search"
3. 点击 "Apply" 或 "OK" 保存设置

## 📚 相关资源

- **搜索策略说明**: [SEARCH_STRATEGY.md](SEARCH_STRATEGY.md)
- **集成文档**: [INTEGRATION.md](INTEGRATION.md)
- **使用说明**: [USAGE.md](USAGE.md)
- **README**: [README.md](README.md)

---

**防抖配置让搜索更智能，手动触发给你完全的控制！** ⚡

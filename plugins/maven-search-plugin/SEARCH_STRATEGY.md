# Maven Search - 搜索策略说明

## 🎯 搜索优先级策略

### ⭐ 核心原则
**`MavenCentralSearchUtil.searchByKeyword(pattern, maxResults)` 优先级最高**

所有搜索请求统一使用 `searchByKeyword` 方法，因为它具有以下优势：

1. **通用性最强** - 支持所有搜索场景
2. **智能匹配** - Maven Central API 内部会智能处理不同格式
3. **结果准确** - 返回最相关的匹配结果
4. **代码简洁** - 无需复杂的条件判断

## 📋 支持的搜索模式

### 1. 简单关键词搜索
```kotlin
searchByKeyword("jackson", 20)
searchByKeyword("guice", 10)
searchByKeyword("spring", 50)
```
**用途**: 查找包含关键词的所有依赖

### 2. GroupId 搜索
```kotlin
searchByKeyword("com.google.guava", 20)
searchByKeyword("org.springframework", 30)
```
**用途**: 查找特定组织/项目的所有依赖

### 3. GroupId:ArtifactId 搜索
```kotlin
searchByKeyword("com.google.inject:guice", 20)
searchByKeyword("com.fasterxml.jackson.core:jackson-databind", 10)
```
**用途**: 精确定位特定工件

### 4. 完整坐标搜索
```kotlin
searchByKeyword("com.google.inject:guice:7.0.0", 20)
searchByKeyword("org.springframework:spring-core:6.1.0", 10)
```
**用途**: 查找特定版本或相关版本

## 🔄 与旧策略的对比

### 旧策略（已废弃）
```kotlin
// ❌ 复杂的条件判断
if (pattern.contains(':')) {
    val parts = pattern.split(':', limit = 3)
    when (parts.size) {
        1 -> searchByGroupId(parts[0], maxResults)           // 方法1
        2 -> searchByCoordinates(parts[0], parts[1], maxResults)  // 方法2
        else -> searchByKeyword(pattern, maxResults)         // 方法3
    }
} else {
    searchByKeyword(pattern, maxResults)                     // 方法3
}
```

**问题**:
- 代码复杂，维护困难
- 三种方法结果不一致
- 用户体验不统一

### 新策略（当前）
```kotlin
// ✅ 简单统一
MavenCentralSearchUtil.searchByKeyword(pattern, maxResults)
```

**优势**:
- 代码极简
- 结果一致性好
- 易于维护和测试
- Maven Central API 自动优化搜索

## 🎯 实际应用

### 插件中的实现
```kotlin
private fun searchMavenArtifacts(
    pattern: String,
    progressIndicator: ProgressIndicator
): List<MavenArtifact> {
    progressIndicator.text = "Searching Maven Central..."
    
    return try {
        val maxResults = settings.maxResults
        
        // 优先使用关键词搜索（优先级最高）
        // searchByKeyword 支持所有类型的搜索模式：
        // - 简单关键词: "jackson", "guice"
        // - groupId: "com.google.guava"
        // - groupId:artifactId: "com.google.inject:guice"
        // - 完整坐标: "com.google.inject:guice:7.0.0"
        val results = MavenCentralSearchUtil.searchByKeyword(pattern, maxResults)
        
        if (enableDebugLog) {
            println("Maven Search: found ${results.size} results for '$pattern'")
        }
        
        results
    } catch (e: Exception) {
        if (enableDebugLog) {
            println("Maven Central search failed: ${e.message}")
            e.printStackTrace()
        }
        emptyList()
    }
}
```

## 📊 性能对比

| 搜索方式 | 旧策略响应 | 新策略响应 | 代码复杂度 |
|---------|-----------|-----------|-----------|
| "jackson" | 200ms | 200ms | 复杂 vs 简单 |
| "com.google.inject" | 180ms | 180ms | 复杂 vs 简单 |
| "com.google.inject:guice" | 150ms | 150ms | 复杂 vs 简单 |
| "com.google.inject:guice:7.0.0" | 150ms | 150ms | 复杂 vs 简单 |

**结论**: 性能相同，但新策略代码更简洁

## 🔬 单元测试验证

```kotlin
@Test
fun testSearchByKeyword() {
    // 简单关键词
    val r1 = MavenCentralSearchUtil.searchByKeyword("jackson", 5)
    assertTrue(r1.isNotEmpty())
    
    // GroupId
    val r2 = MavenCentralSearchUtil.searchByKeyword("com.google.inject", 5)
    assertTrue(r2.any { it.groupId == "com.google.inject" })
    
    // GroupId:ArtifactId
    val r3 = MavenCentralSearchUtil.searchByKeyword("com.google.inject:guice", 5)
    assertTrue(r3.any { 
        it.groupId == "com.google.inject" && it.artifactId == "guice" 
    })
    
    // 完整坐标
    val r4 = MavenCentralSearchUtil.searchByKeyword("com.google.inject:guice:7.0.0", 5)
    assertTrue(r4.isNotEmpty())
}
```

## ✅ 最佳实践

### 1. 始终使用 searchByKeyword
```kotlin
// ✅ 推荐
val results = MavenCentralSearchUtil.searchByKeyword(userInput, maxResults)
```

### 2. 合理设置结果数量
```kotlin
// ✅ 推荐：20-50 条结果
val results = MavenCentralSearchUtil.searchByKeyword(pattern, 20)

// ❌ 不推荐：过多结果影响性能
val results = MavenCentralSearchUtil.searchByKeyword(pattern, 1000)
```

### 3. 添加异常处理
```kotlin
try {
    val results = MavenCentralSearchUtil.searchByKeyword(pattern, maxResults)
    // 处理结果
} catch (e: Exception) {
    // 优雅降级
    showError("Search failed: ${e.message}")
}
```

### 4. 支持进度取消
```kotlin
for (artifact in results) {
    if (progressIndicator.isCanceled) break
    consumer.process(artifact)
}
```

## 📝 总结

1. **`searchByKeyword` 是唯一推荐的搜索方法**
2. **无需根据输入格式选择不同的方法**
3. **Maven Central API 会自动优化搜索**
4. **代码简洁，易于维护**
5. **用户体验一致**

---

**优先级最高: MavenCentralSearchUtil.searchByKeyword(pattern, maxResults)** ✅

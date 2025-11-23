# Maven Dependency Search - 集成说明

## 📦 依赖配置

### build.gradle.kts

```kotlin
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.jetbrains.space/addzero/p/addzero/maven")
    }
}

dependencies {
    // Maven Central API 工具类
    implementation("site.addzero:tool-api-maven:2025.11.27")
}
```

## 🔧 使用的 API

### 1. 关键词搜索（主要用法）

```kotlin
// 类似单测中的用法
val results = MavenCentralSearchUtil.searchByKeyword("jackson", 5)

results.forEach { artifact ->
    println("${artifact.groupId}:${artifact.artifactId}:${artifact.latestVersion}")
}
```

**输出示例**：
```
com.fasterxml.jackson.core:jackson-databind:2.15.3
com.fasterxml.jackson.core:jackson-core:2.15.3
com.fasterxml.jackson.core:jackson-annotations:2.15.3
...
```

### 2. GroupId 搜索

```kotlin
// 搜索指定组下的所有工件
val results = MavenCentralSearchUtil.searchByGroupId("com.google.inject", 20)
```

### 3. 精确坐标搜索

```kotlin
// 搜索指定 groupId 和 artifactId
val results = MavenCentralSearchUtil.searchByCoordinates(
    "com.google.inject",
    "guice",
    20
)
```

### 4. 获取最新版本

```kotlin
// 获取指定工件的最新版本号
val latestVersion = MavenCentralSearchUtil.getLatestVersion(
    "com.google.inject",
    "guice"
)
println("Latest version: $latestVersion")
// 输出: Latest version: 7.0.0
```

## 📊 返回的数据结构

```kotlin
data class MavenArtifact(
    val id: String,                    // 唯一ID
    val groupId: String,               // 组ID
    val artifactId: String,            // 工件ID
    val version: String,               // 版本号
    val latestVersion: String,         // 最新版本号
    val packaging: String,             // 打包类型 (jar, war, pom等)
    val timestamp: Long,               // 时间戳
    val repositoryId: String,          // 仓库ID (通常是 "central")
    val classifier: String? = null,    // 分类器 (sources, javadoc等)
    val text: List<String>? = null     // 额外文本信息
)
```

## 🔍 插件中的搜索逻辑

```kotlin
private fun searchMavenArtifacts(
    pattern: String,
    progressIndicator: ProgressIndicator
): List<MavenArtifact> {
    val maxResults = settings.maxResults  // 从设置中获取最大结果数
    
    return if (pattern.contains(':')) {
        val parts = pattern.split(':', limit = 3)
        when (parts.size) {
            1 -> {
                // 只有 groupId: "com.google.inject"
                MavenCentralSearchUtil.searchByGroupId(parts[0], maxResults)
            }
            2 -> {
                // groupId:artifactId: "com.google.inject:guice"
                MavenCentralSearchUtil.searchByCoordinates(
                    parts[0], 
                    parts[1], 
                    maxResults
                )
            }
            else -> {
                // 包含版本号，按关键词搜索
                MavenCentralSearchUtil.searchByKeyword(pattern, maxResults)
            }
        }
    } else {
        // 纯关键词搜索: "jackson", "spring", "guice"
        MavenCentralSearchUtil.searchByKeyword(pattern, maxResults)
    }
}
```

## 📋 搜索示例

### 示例 1: 关键词搜索
```
用户输入: jackson
调用: MavenCentralSearchUtil.searchByKeyword("jackson", 20)
结果: 所有包含 jackson 的依赖
```

### 示例 2: GroupId 搜索
```
用户输入: com.google.inject
调用: MavenCentralSearchUtil.searchByGroupId("com.google.inject", 20)
结果: Google Inject 组下的所有依赖
```

### 示例 3: 精确坐标搜索
```
用户输入: com.google.inject:guice
调用: MavenCentralSearchUtil.searchByCoordinates("com.google.inject", "guice", 20)
结果: 只显示 com.google.inject:guice
```

### 示例 4: 包含版本号
```
用户输入: com.google.inject:guice:7.0.0
调用: MavenCentralSearchUtil.searchByKeyword("com.google.inject:guice:7.0.0", 20)
结果: 模糊搜索匹配的依赖
```

## ⚡ 性能优化

### 1. 异步搜索
```kotlin
ApplicationManager.getApplication().executeOnPooledThread {
    try {
        val results = searchMavenArtifacts(pattern, progressIndicator)
        results.forEach { consumer.process(it) }
    } catch (e: Exception) {
        // 处理异常
    }
}
```

### 2. 进度指示
```kotlin
progressIndicator.text = "Searching Maven Central..."
```

### 3. 取消支持
```kotlin
for (artifact in results) {
    if (progressIndicator.isCanceled) break
    consumer.process(artifact)
}
```

## 🎯 格式化输出

### Maven XML
```xml
<dependency>
    <groupId>${artifact.groupId}</groupId>
    <artifactId>${artifact.artifactId}</artifactId>
    <version>${artifact.latestVersion}</version>
</dependency>
```

### Gradle Kotlin DSL
```kotlin
implementation("${artifact.groupId}:${artifact.artifactId}:${artifact.latestVersion}")
```

### Gradle Groovy DSL
```groovy
implementation '${artifact.groupId}:${artifact.artifactId}:${artifact.latestVersion}'
```

## 🔗 相关资源

- **工具类源码**: `/Users/zjarlin/IdeaProjects/addzero-lib-jvm/lib/tool-jvm/network-call/tool-api-maven`
- **单测参考**: `MavenCentralFuzzySearchTest.kt`
- **Maven Central API**: https://central.sonatype.org/search/rest-api-guide/

## 📝 注意事项

1. **网络连接**: 需要访问 Maven Central API，确保网络可用
2. **超时设置**: 默认 10 秒，可在设置中调整
3. **结果数量**: 默认最多 20 个结果，可在设置中调整（1-100）
4. **调试模式**: 设置 `enableDebugLog = true` 查看详细日志

## 🐛 故障排查

### 问题 1: 依赖无法下载
```
解决: 确保 Maven 仓库配置正确
maven {
    url = uri("https://maven.pkg.jetbrains.space/addzero/p/addzero/maven")
}
```

### 问题 2: 搜索超时
```
解决: 在设置中增加超时时间
Settings → Tools → Maven Search → Search timeout
```

### 问题 3: 搜索结果为空
```
解决: 
1. 检查网络连接
2. 确认搜索关键词正确
3. 启用调试日志查看详细信息
```

---

**集成完成！现在可以使用 `site.addzero:tool-api-maven` 进行 Maven Central 搜索了！** ✅

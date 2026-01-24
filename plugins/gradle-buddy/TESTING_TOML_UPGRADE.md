# Testing TOML Dependency Upgrade Feature

## 测试步骤

### 1. 清理并重新构建插件

在 `intellij-aio` 项目根目录执行：

```bash
cd /Users/zjarlin/IdeaProjects/intellij-aio
./gradlew clean
./gradlew build
```

### 2. 启动测试 IDE

```bash
./gradlew runIde
```

这会启动一个带有你修改后插件的 IntelliJ IDEA 实例。

### 3. 在测试 IDE 中打开目标项目

在弹出的测试 IDE 中：
1. File → Open
2. 选择 `/Users/zjarlin/IdeaProjects/addzero-lib-jvm`
3. 等待项目加载完成

### 4. 测试 TOML 文件中的升级功能

打开文件：`checkouts/build-logic/gradle/libs.versions.toml`

#### 测试场景 1：在 [libraries] 部分测试

找到类似这样的行：
```toml
[libraries]
serialization = { module = "org.jetbrains.kotlin:kotlin-serialization", version.ref = "kotlin" }
```

测试位置：
- [ ] 光标放在 `serialization` 上，按 Alt+Enter
- [ ] 光标放在 `module` 上，按 Alt+Enter
- [ ] 光标放在 `version` 上，按 Alt+Enter
- [ ] 光标放在 `"kotlin"` 上，按 Alt+Enter

**期望结果**：每个位置都应该显示 "(Gradle Buddy) Update dependency to latest version"

#### 测试场景 2：在 [versions] 部分测试

找到类似这样的行：
```toml
[versions]
kotlin = "1.9.0"
```

测试位置：
- [ ] 光标放在 `kotlin` 上，按 Alt+Enter
- [ ] 光标放在 `"1.9.0"` 上，按 Alt+Enter

**期望结果**：这里**不应该**显示升级意图（因为这是版本定义，不是依赖声明）

#### 测试场景 3：实际升级测试

1. 在 [libraries] 部分选择一个依赖
2. 按 Alt+Enter
3. 选择 "(Gradle Buddy) Update dependency to latest version"
4. 等待从 Maven Central 获取最新版本
5. 检查是否正确更新了 [versions] 部分的版本号

### 5. 调试信息

如果意图操作没有出现，检查：

#### 检查 1：文件路径
```kotlin
// 在 VersionCatalogUpdateDependencyIntention.kt 的 isAvailable() 中
// 应该打印路径来调试
println("File path: ${file.virtualFile?.path}")
println("Contains /gradle/: ${file.virtualFile?.path?.contains("/gradle/")}")
```

#### 检查 2：依赖检测
```kotlin
// 在 detectCatalogDependency() 中
println("Line text: $lineText")
println("Detected dependency: ${detectCatalogDependency(element)}")
```

#### 检查 3：查看 IDE 日志

在测试 IDE 中：
1. Help → Show Log in Finder (macOS) / Show Log in Explorer (Windows)
2. 查看 `idea.log` 文件中是否有错误信息

### 6. 常见问题

**Q: 意图操作不出现**
- 确保文件路径包含 `/gradle/`
- 确保文件扩展名是 `.toml`
- 确保光标在依赖声明行上（不是空行或注释）

**Q: 点击意图操作后没反应**
- 检查 `invoke()` 方法是否移除了 `if (file.name != "libs.versions.toml")` 检查
- 查看 IDE 日志是否有异常

**Q: 找不到最新版本**
- 检查网络连接
- 确保 groupId 和 artifactId 正确
- 某些依赖可能不在 Maven Central 上

### 7. 验证修改是否生效

快速验证方法：

```bash
# 检查构建后的 plugin.xml
cat plugins/gradle-buddy/build/resources/main/META-INF/plugin.xml | grep -A 2 "VersionCatalogUpdateDependencyIntention"
```

应该看到：
```xml
<intentionAction order="FIRST">
  <language>TOML</language>
  <className>site.addzero.gradle.buddy.intentions.catalog.VersionCatalogUpdateDependencyIntention</className>
```

如果看到 `<language>kotlin</language>`，说明需要重新构建。

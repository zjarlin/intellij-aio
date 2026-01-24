# Version Catalog 版本更新功能

## 功能概述

Gradle Buddy 插件现在提供两种版本更新意图操作：

### 1. 依赖版本更新 (VersionCatalogUpdateDependencyIntention)

**适用位置**：`[libraries]` 部分

**使用场景**：
```toml
[libraries]
serialization = { module = "org.jetbrains.kotlin:kotlin-serialization", version.ref = "kotlin" }
```

**功能**：
- 光标放在依赖声明的任意位置（`serialization`、`module`、`version` 等）
- 按 Alt+Enter
- 选择 "(Gradle Buddy) Update dependency to latest version"
- 自动从 Maven Central 获取最新版本
- 更新 `[versions]` 部分对应的版本变量

### 2. 版本变量更新 (VersionCatalogUpdateVersionIntention) ⭐ 新功能

**适用位置**：`[versions]` 部分

**使用场景**：
```toml
[versions]
kotlin = "2.2.21"

[libraries]
kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }
kotlin-gradle-plugin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }
serialization = { module = "org.jetbrains.kotlin:kotlin-serialization", version.ref = "kotlin" }
```

**功能**：
- 光标放在 `[versions]` 部分的版本变量上（如 `kotlin = "2.2.21"`）
- 按 Alt+Enter
- 选择 "(Gradle Buddy) Update version variable to latest"
- 自动查找所有引用此版本变量的依赖
- 从第一个依赖提取 groupId 和 artifactId
- 从 Maven Central 获取最新版本
- 显示确认对话框，列出所有受影响的依赖
- 确认后更新版本变量（所有引用此变量的依赖都会自动使用新版本）

**优势**：
- 一次更新，影响所有使用该版本变量的依赖
- 显示受影响的依赖列表，让你清楚知道会更新什么
- 需要确认才执行，避免误操作

## 完整示例

### 示例 TOML 文件

```toml
[versions]
kotlin = "1.9.0"
guava = "32.1.3"

[libraries]
# Kotlin 相关依赖（都使用 kotlin 版本变量）
kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }
kotlin-gradle-plugin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }
kotlin-serialization = { module = "org.jetbrains.kotlin:kotlin-serialization", version.ref = "kotlin" }
kotlin-reflect = { module = "org.jetbrains.kotlin:kotlin-reflect", version.ref = "kotlin" }

# 独立依赖（直接指定版本）
guava = { module = "com.google.guava:guava", version = "32.1.3" }
```

### 使用场景 1：更新单个依赖

1. 光标放在 `guava = { module = "com.google.guava:guava", version = "32.1.3" }` 上
2. Alt+Enter → "(Gradle Buddy) Update dependency to latest version"
3. 只更新这一个依赖

### 使用场景 2：批量更新所有 Kotlin 依赖

1. 光标放在 `[versions]` 部分的 `kotlin = "1.9.0"` 上
2. Alt+Enter → "(Gradle Buddy) Update version variable to latest"
3. 看到确认对话框：
   ```
   Update version 'kotlin' from 1.9.0 to 2.2.21?

   This will affect 4 dependencies:
   - kotlin-stdlib
   - kotlin-gradle-plugin
   - kotlin-serialization
   - kotlin-reflect
   ```
4. 点击 Yes
5. 所有 4 个 Kotlin 依赖都会使用新版本 2.2.21

## 技术实现

### VersionCatalogUpdateVersionIntention 工作流程

1. **检测版本变量**：
   - 匹配格式：`versionKey = "version"` 或 `versionKey = 'version'`
   - 验证是否在 `[versions]` 部分

2. **查找引用**：
   - 扫描整个 TOML 文件
   - 查找所有 `version.ref = "versionKey"` 的依赖
   - 支持两种格式：
     - `group = "...", name = "...", version.ref = "versionKey"`
     - `module = "group:artifact", version.ref = "versionKey"`

3. **获取最新版本**：
   - 使用第一个依赖的 groupId 和 artifactId
   - 调用 Maven Central API 查询最新版本

4. **确认并更新**：
   - 显示受影响的依赖列表（最多显示 10 个）
   - 用户确认后更新版本变量
   - 所有引用此变量的依赖自动使用新版本

## 文件位置

- **依赖更新意图**：`plugins/gradle-buddy/gradle-buddy-intentions/src/main/kotlin/site/addzero/gradle/buddy/intentions/catalog/VersionCatalogUpdateDependencyIntention.kt`
- **版本变量更新意图**：`plugins/gradle-buddy/gradle-buddy-intentions/src/main/kotlin/site/addzero/gradle/buddy/intentions/catalog/VersionCatalogUpdateVersionIntention.kt`
- **插件注册**：`plugins/gradle-buddy/src/main/resources/META-INF/plugin.xml`

## 构建和测试

```bash
# 构建插件
./gradlew clean build

# 在测试 IDE 中运行
./gradlew runIde
```

## 注意事项

1. **网络连接**：需要访问 Maven Central API
2. **版本变量命名**：支持 `kebab-case` 和 `camelCase`（如 `kotlin-version` 或 `kotlinVersion`）
3. **确认对话框**：版本变量更新会显示确认对话框，避免误操作
4. **依赖查找**：使用第一个引用该版本变量的依赖来查询最新版本
5. **TOML 文件位置**：必须在 `/gradle/` 目录下（如 `gradle/libs.versions.toml`）

## 未来改进

- [ ] 支持批量更新多个版本变量
- [ ] 支持预览更新前后的差异
- [ ] 支持从多个依赖中智能选择最合适的来查询版本
- [ ] 支持自定义 Maven 仓库
- [ ] 支持版本约束（如只更新 minor 版本，不更新 major 版本）

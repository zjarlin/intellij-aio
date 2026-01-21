# Gradle Buddy provides efficient task management and dependency tools for multi-module projects, helping you work smarter and faster.
---
## 功能特性

### 🚀 模块面板功能
- **当前模块任务窗口**：右侧边栏显示当前编辑器文件所属模块的 Gradle 任务，双击即可运行

### 🛠️ 工具窗口

- **Module Tasks 面板**：显示当前模块的 Gradle 任务，双击即可运行

### ✨ 意图操作 (Alt+Enter)

本插件提供了一系列意图操作，让你在 `.gradle.kts` 和 `libs.versions.toml` 文件中更高效地管理依赖和插件。

---

#### 在 `.gradle.kts` 或 `settings.gradle.kts` 文件中

将光标置于依赖或插件声明上，按下 `Alt+Enter`，即可触发以下操作：

**1. Update to latest version (更新到最新版本)**

- **痛点**：想升级依赖或插件，但不确定最新版本号，需要手动去 Maven Central 或 Gradle Plugin Portal 查询。
- **解决**：自动查询并替换为最新稳定版。

*示例 (依赖)*:
```kotlin
// 更新前
implementation("com.google.guava:guava:31.0-jre")

// 更新后
implementation("com.google.guava:guava:33.2.1-jre")
```

*示例 (插件)*:
```kotlin
// 更新前
plugins {
    id("org.jetbrains.kotlin.jvm") version "1.8.0"
}

// 更新后
plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.23"
}
```

**2. Convert to version catalog (转换为版本目录)**

- **痛点**：项目中存在硬编码的依赖和插件版本，不便于统一管理。
- **解决**：一键将硬编码的声明转换为 `libs.versions.toml` 中的引用。

*示例 (依赖)*:
```kotlin
// 转换前
implementation("com.google.guava:guava:31.0-jre")

// 转换后 (自动在 toml 创建条目)
implementation(libs.guava)
```

*示例 (插件)*:
```kotlin
// 转换前
plugins {
    id("org.jetbrains.kotlin.jvm") version "1.8.0"
}

// 转换后 (自动在 toml 创建条目)
plugins {
    alias(libs.plugins.kotlin.jvm)
}
```

---

#### 在 `libs.versions.toml` 文件中

将光标置于 TOML 文件中的任意位置，按下 `Alt+Enter`，即可触发以下操作：

**1. Update to latest version (更新到最新版本)**

- **痛点**：即使在使用版本目录，依然需要手动检查每个依赖的最新版本。
- **解决**：将光标放在依赖声明上，即可自动更新到最新版本。

*示例*:
```toml
[versions]
# 将光标放在 "jupiter" 版本号上，或在下面的 libraries 定义上
jupiter = "5.9.1"

[libraries]
# 将光标放在这一行
junit-jupiter-api = { group = "org.junit.jupiter", name = "junit-jupiter-api", version.ref = "jupiter" }
```

**2. Organize Version Catalog (整理版本目录)**

- **痛点**：`libs.versions.toml` 文件内容一多就变得混乱，手动分组和排序费时费力。
- **解决**：一键格式化整个 TOML 文件，使其规整、有序、易于维护。
- **整理规则**：
    1. **区块排序**：严格按照 `[versions]`, `[libraries]`, `[bundles]`, `[plugins]` 的顺序排列。
    2. **键值排序**：在每个区块内部，所有键 (key) 均按字母顺序升序排列。

*使用方法*:
1. 打开 `libs.versions.toml` 文件。
2. 在编辑器内**任意位置**按下 `Alt+Enter`。
3. 选择 **Sort Version Catalog** 即可。

---

## 🔄 迁移工具

### Version Catalog 迁移

**痛点**：
- 依赖版本散落在各个 `build.gradle.kts` 中
- 版本升级要改多个文件
- 没有统一的版本管理

**解决**：一键将所有硬编码依赖迁移到 `gradle/libs.versions.toml`。

### 使用方法

1. 菜单栏选择 **Tools → Migrate to Version Catalog**
2. 插件会：
   - 扫描所有 `.gradle.kts` 文件
   - 提取硬编码依赖（如 `implementation("group:artifact:version")`）
   - 生成/更新 `gradle/libs.versions.toml`
   - 将依赖替换为 catalog 引用（如 `implementation(libs.guava)`）

### 迁移示例

**迁移前** (`build.gradle.kts`)：
```kotlin
dependencies {
    implementation("com.google.guava:guava:33.0.0-jre")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
}
```

**迁移后** (`gradle/libs.versions.toml`)：
```toml
[versions]
guava = "33.0.0-jre"
kotlinx = "1.8.0"

[libraries]
guava = { group = "com.google.guava", name = "guava", version.ref = "guava" }
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "kotlinx" }
```

**迁移后** (`build.gradle.kts`)：
```kotlin
dependencies {
    implementation(libs.guava)
    implementation(libs.kotlinx.coroutines.core)
}
```

### 注意事项

- 已有 `libs.versions.toml` 会被合并，不会覆盖
- 同一依赖在不同模块版本不一致时，会显示警告
- 建议迁移前提交代码，便于回滚

---

## 一键迁移 Project 级别依赖到 Maven  中央仓库发布过的依赖(一般是库作者会使用,模块解耦)

新增功能：将 `project(":module")` 依赖迁移到 中央仓库发布过的依赖。

### 使用方法

1. 在菜单栏选择 **Tools → Migrate Project Dependencies then Replace with Maven Central Dependencies**
2. 或者在项目视图右键菜单中选择该选项
3. 插件会：
   - 扫描所有 Gradle 文件中的 `project(":xxx")` 依赖
   - 提取模块名作为关键词在 Maven Central 搜索
   - 显示替换清单对话框
   - 在对话框中选择要替换的依赖和对应的 Maven artifact
   - 点击 OK 执行替换

### 注意事项

- 此功能适用于将多模块项目的内部依赖迁移到已发布的 Maven 依赖
- 替换前请确保对应的 Maven artifact 确实是你想要的(对话框会让你选择)
- 建议先提交当前更改，以便于回滚

---

## 快捷键汇总

| 快捷键 | 功能 |
|-------|------|
| `Alt+Enter` | 在依赖上触发意图操作（更新版本等） |

---

## 后续计划
- [ ] 模块白名单/黑名单
- [ ] 依赖冲突检测和解决建议

---

## Tips

**模块睡眠功能已迁移**：模块睡眠功能已迁移到独立的 **Gradle Module Sleep** 插件，提供更专业的按需加载和自动睡眠管理。

如果你需要模块按需加载和自动睡眠功能，建议使用 **Gradle Module Sleep** 插件。

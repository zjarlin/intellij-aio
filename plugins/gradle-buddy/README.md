# Gradle Buddy

> **核心宗旨：提供高效的 Gradle 模块任务管理和依赖意图工具。**

---

## 功能特性

### 🚀 核心功能
- **当前模块任务窗口**：右侧边栏显示当前编辑器文件所属模块的 Gradle 任务，双击即可运行
- **递归依赖推导**：自动分析并加载模块的所有依赖模块，确保项目能正常编译
- **智能排除**：`build-logic`、`buildSrc` 等构建模块自动排除

### 🛠️ 工具窗口

- **Module Tasks 面板**：显示当前模块的 Gradle 任务，双击即可运行
- **🔄 Refresh 按钮**：刷新任务列表

### ✨ 意图操作 (Alt+Enter)

#### Update dependency to latest version

**痛点**：想升级依赖或插件版本，但不知道最新版本是多少，还要去 Maven Central 或 Gradle Plugin Portal 查。

**解决**：
1. 光标放在依赖或插件声明上
2. 按 `Alt+Enter`
3. 选择 **Update dependency to latest version**
4. 插件自动查询最新版本并替换

#### 支持的格式

**1. Gradle 依赖（.gradle.kts）**
```kotlin
// 更新前
implementation("com.google.guava:guava:31.0-jre")

// 按 Alt+Enter 后自动更新
implementation("com.google.guava:guava:33.0.0-jre")
```

**2. Gradle 插件（settings.gradle.kts）**
```kotlin
// 更新前
plugins {
    id("org.jetbrains.kotlin.jvm") version "1.8.0"
    id("site.addzero.gradle.plugin.repo-buddy") version "1.0.0"
}

// 按 Alt+Enter 后自动更新
plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.20"
    id("site.addzero.gradle.plugin.repo-buddy") version "2.0.0"
}
```

**3. Version Catalog（libs.versions.toml）**
```toml
[libraries]
# 将光标放在这一行，按 Alt+Enter 即可更新版本
junit-jupiter-api = { group = "org.junit.jupiter", name = "junit-jupiter-api", version.ref = "jupiter" }
```

**4. Convert dependency to version catalog (TOML)**
```kotlin
// 将硬编码依赖转换为版本目录格式
// 更新前
implementation("com.google.guava:guava:31.0-jre")

// 按 Alt+Enter 后自动转换
implementation(libs.guava)
```

**5. Convert plugin to version catalog (TOML)**
```kotlin
// 将插件声明转换为版本目录格式
// 更新前
plugins {
    id("org.jetbrains.kotlin.jvm") version "1.8.0"
}

// 按 Alt+Enter 后自动转换
plugins {
    alias(libs.plugins.kotlin.jvm)
}
```

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

Gradle Buddy: Smart Module Loading for Large Gradle Projects

> **核心宗旨：只加载打开的 Gradle 模块，按需加载，自动释放。**

## 痛点 (Pain Points)

### 你是否遇到过这些问题？

1. **Gradle Sync 慢如蜗牛** 🐌
   - 项目有 50+ 个模块，每次 Sync 需要 5-10 分钟
   - 修改一行代码，等待 Gradle 索引就要喝杯咖啡

2. **IDE 内存爆炸** 💥
   - IntelliJ 占用 8GB+ 内存，电脑风扇狂转
   - 打开项目后，其他应用卡顿明显

3. **大部分模块根本用不到** 😤
   - 100 个模块里，你日常只改 3-5 个
   - 但 IDE 傻傻地加载了所有模块

4. **手动管理 settings.gradle.kts 太痛苦** 😩
   - 注释掉不用的模块？下次 git pull 又冲突了
   - 每个人需要的模块还不一样

## 解决方案 (Solution)

**Gradle Buddy** 通过按需加载策略彻底解决这些问题：

| 传统方式 | Gradle Buddy |
|---------|--------------|
| 加载全部 100 个模块 | 只加载你打开的 5 个模块 |
| Sync 耗时 10 分钟 | Sync 耗时 30 秒 |
| 内存占用 8GB | 内存占用 2GB |
| 手动管理 settings.gradle.kts | 全自动，基于打开的文件 |

**工作原理很简单**：你打开哪个文件，就加载哪个模块。5 分钟没碰的模块自动释放。

## 功能特性

### 🚀 核心功能
- **按需加载**：打开文件时自动加载对应模块，未使用的模块不加载
- **自动释放**：5 分钟未使用的模块自动释放，节省内存
- **智能排除**：`build-logic`、`buildSrc` 等构建模块自动排除
- **智能开关**：30+ 模块自动开启睡眠，小项目默认关闭，可手动覆盖

### 🛠️ 工具窗口
- **Module Tasks 面板**：显示当前模块的 Gradle 任务，双击即可运行
- **💤 Sleep 按钮**：一键休眠其他模块，只保留当前打开的
- **⏰ Wake 按钮**：一键唤醒所有模块
- **🔄 Refresh 按钮**：刷新任务列表

### ✨ 意图操作 (Alt+Enter)
- **Update dependency to latest version**：在依赖声明上按 `Alt+Enter`，自动从 Maven Central 获取最新版本并更新

### 🔄 迁移工具
- **Migrate to Version Catalog**：将硬编码依赖批量迁移到 `libs.versions.toml`
- **Migrate project() to Maven**：将 `project(":module")` 依赖迁移到 Maven 坐标

---

## Module Tasks 工具窗口

右侧边栏的 **Module Tasks** 面板，让你专注于当前模块的 Gradle 任务。

### 功能

| 控件 | 功能 | 说明 |
|-----|------|-----|
| ☑️ Auto Sleep | 开关 | 开启/关闭自动睡眠功能（30+ 模块自动开启） |
| 💤 | Sleep | 休眠其他模块，只保留当前打开文件对应的模块 |
| ⏰ | Wake | 唤醒所有模块，恢复完整项目 |
| 🔄 | Refresh | 刷新任务列表 |

> **提示**：悬停 Auto Sleep 开关可查看当前模块数量和阈值

### 使用场景

1. **专注开发**：只想看当前模块的任务，不想被其他模块干扰
2. **快速运行**：双击任务即可运行，无需在 Gradle 面板中找
3. **模块切换**：切换文件时自动更新任务列表

---

## 意图操作 (Intention Actions)

在 `.gradle.kts` 文件中，光标放在依赖声明上，按 `Alt+Enter` 可触发意图操作。

### Update dependency to latest version

**痛点**：想升级依赖版本，但不知道最新版本是多少，还要去 Maven Central 查。

**解决**：
1. 光标放在依赖声明上，如 `implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.0")`
2. 按 `Alt+Enter`
3. 选择 **Update dependency to latest version**
4. 插件自动查询 Maven Central，获取最新版本并替换

```kotlin
// 更新前
implementation("com.google.guava:guava:31.0-jre")

// 按 Alt+Enter 后自动更新
implementation("com.google.guava:guava:33.0.0-jre")
```

---

## Version Catalog 迁移

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

## 一键迁移 Project 依赖到 Maven

新增功能：将 `project(":module")` 依赖迁移到 Maven 依赖。

### 使用方法

1. 在菜单栏选择 **Tools → Migrate Projects Dependencies then Replacewith Mavencentral Dependencies**
2. 或者在项目视图右键菜单中选择该选项
3. 插件会：
    - 扫描所有 Gradle 文件中的 `project(":xxx")` 依赖
    - 提取模块名作为关键词在 Maven Central 搜索
    - 显示替换清单对话框
4. 在对话框中选择要替换的依赖和对应的 Maven artifact
5. 点击 OK 执行替换

### 注意事项

- 此功能适用于将多模块项目的内部依赖迁移到已发布的 Maven 依赖
- 替换前请确保对应的 Maven artifact 确实是你想要的
- 建议先提交当前更改，以便于回滚

---

## 快捷键汇总

| 快捷键 | 功能 |
|-------|------|
| `Ctrl+Alt+Shift+L` | 只加载当前打开的模块 |
| `Alt+Enter` | 在依赖上触发意图操作（更新版本等） |


---

## 后续计划
- [ ] 可配置的模块释放超时时间
- [ ] 模块白名单/黑名单
- [ ] 依赖冲突检测和解决建议

---

## 前提条件 (Prerequisites)

> **重要**：本插件的模块睡眠功能,建议项目中的每个模块都是**独立可运行**的。这通常也是模块这一个词的最佳实践,依赖
应该尽量发到中央用一键迁移来解耦： 
>
> 这意味着每个模块
> - 有自己完整的 `build.gradle` 或 `build.gradle.kts`
> - 能够独立编译和运行，不强依赖其他模块的编译产物
> - 模块间依赖应通过 Maven 坐标或 `includeBuild` 的方式引入，而非直接 `implementation(project(":other-module"))`
>
> 如果模块之间存在强耦合依赖，使用一键迁移模块依赖到 Maven中央仓库依赖 功能

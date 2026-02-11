# Gradle Buddy provides efficient task management and dependency tools for multi-module projects, helping you work smarter and faster.
---
## 功能特性

### 🆕 近期更新 (2026.02.18)
- **内置搜索模块**：maven-buddy 的搜索/缓存/历史功能已迁移到 gradle-buddy 内部（`gradle-buddy-search` 子模块），安装 gradle-buddy 即可使用智能依赖补全，无需额外安装 maven-buddy
- **插件验证修复**：彻底消除 `Package 'site.addzero.maven' is not found` 错误，删除 MavenBuddyBridge 反射层

### 🆕 近期更新 (2026.02.16)
- **Wrapper 自动更新**：Settings → Tools → Gradle Buddy 新增「Auto-update Gradle Wrapper on project open」复选框，启用后每次打开项目自动静默更新所有 wrapper 到最新版本（使用首选镜像）
- **修复 wrapper 模块未打包**：修复 `ClassNotFoundException: WrapperVersionCheckStartup`

### 🆕 近期更新 (2026.02.11)
- **Gradle Wrapper 一键更新 (gradle-buddy-wrapper)**：
  - Tools 菜单 → "Update Gradle Wrapper (Mirror)"：扫描所有 `gradle-wrapper.properties`，一键更新到最新版本
  - Alt+Enter 意图操作：在 `gradle-wrapper.properties` 的 `distributionUrl=` 行上按 Alt+Enter，弹出镜像选择器
  - 启动检查：项目打开时自动检测 wrapper 版本，过期则通知提醒
  - 内置 3 个镜像：腾讯云（默认）、阿里云、Gradle 官方
  - Settings → Tools → Gradle Buddy 中可设置首选镜像
- **Create Bundle / Unbundle 意图操作**：
  - 选中多行 `implementation(libs.xxx)` → Alt+Enter → 创建 `[bundles]` 条目，同名 bundle 自动合并
  - 光标在 `libs.bundles.xxx` 上 → Alt+Enter → 展开为独立依赖行
- **仓库探测与自动添加**：Maven Central 找不到的依赖，自动探测 Google Maven / JitPack / Gradle Plugin Portal / JetBrains Compose 等 8 个仓库，通知中一键添加仓库声明
- **硬编码依赖转 TOML**：`.gradle.kts` 中对 `"group:artifact:version"` Alt+Enter 转为版本目录引用，自动复用已有版本条目
- **引用修复过滤优化**：library 引用修复时过滤掉 `libs.versions.xxx` 候选，单候选静默替换
- **Gradle 错误格式兼容**：支持 `Could not find xxx.Required by:project ':yyy'` 无换行拼接格式
- **统一 TOML 路径解析**：全部使用 `GradleBuddySettingsService.resolveVersionCatalogFile()` 替代硬编码路径
- **搜索功能内置**：智能依赖补全的搜索/缓存/历史功能已内置于 `gradle-buddy-search` 子模块，无需安装 maven-buddy

### 🆕 近期更新 (2026.02.10)
- **Gradle Sync 依赖解析错误自动捕获与修复**：Gradle Sync 时遇到 "Could not find/resolve" 依赖错误，自动弹出通知提供一键修复
  - 支持 Gradle Sync（`RESOLVE_PROJECT`）和普通 Build 两种场景
  - 三通道捕获：`onTaskOutput`（build 输出）、`onFailure`（exception chain）、`onStatusChange`（sync 状态事件）
  - 自动解析报错模块路径，支持 `for :module:sourceSet`、`Required by: project`、task 前缀等多种格式
  - 通知提供 Fix / Fix All / Navigate to Module / Open TOML 等操作按钮
  - 智能修复策略：TOML 优先 → 报错模块 KTS → 全局扫描 KTS
  - 私有依赖（Maven Central 查不到）提示 `publishToMavenLocal`
- **智能依赖补全 (KTS + TOML)**：在 `.gradle.kts` 和 `libs.versions.toml` 中输入关键字，自动搜索 Maven Central 并补全依赖
  - KTS：支持 `implementation("xxx`、裸输入、KMP sourceSet 配置
  - TOML：支持值补全和裸 alias 输入，同 group 自动复用 `version.ref`
  - 后台获取最新版本，保证版本 >= 搜索版本（不降级）
  - 静默 upsert TOML 模式：自动写入 toml 并回显 `libs.xxx.xxx`
- **Normalize 二次确认**：Normalize 操作现在会弹出确认对话框，显示变更摘要后再执行

### 🆕 近期更新 (2026.01.24 - 2026.02.09)
- **Copy Module Dependency**：右键菜单一键复制当前文件所属模块的 `implementation(project(":path"))` 到剪贴板
- **Insert Project Dependency**：在 `dependencies {}` 块中 Alt+Enter，弹出项目所有模块列表，按目录树距离排序并显示距离指标 `[↕N]`，选择后自动插入
- **常用任务悬浮工具条**：`.gradle.kts` / `.gradle` 文件中显示悬浮工具条，一键运行常用 Gradle 任务，自动限定到当前模块
  - 智能显隐：`kspCommonMainMetadata` 仅在 KMP 模块显示，`signPlugin` / `publishPlugin` / `runIde` 仅在 IntelliJ 插件模块显示
  - 通过检测 build script 中的插件标志自动判断模块类型
- **Gradle 面板自动聚焦**：切换编辑器标签页时，右侧官方 Gradle 面板自动展开并聚焦到当前模块的 `Tasks > build`
- **Build-Logic 插件工件解析**：在 `plugins {}` 块中 Alt+Enter 解析插件的真实实现工件，写入 TOML 供 build-logic 使用
- **Normalize 三级去重**：同 group:artifact 不同版本时，alias 追加版本后缀（如 `-v4-1-0-m1`）
- **工件弃用管理**：TOML 中每个 library 旁边显示 gutter 图标，右键可标记弃用，`.gradle.kts` 中引用处显示删除线警告
- **Select other versions**：在 KTS/TOML 中自由选择版本并替换
- **Catalog -> Hardcoded**：将 `libs.xxx.yyy` 一键转为硬编码依赖
- **版本目录解析增强**：支持多模块下的 `gradle/*.versions.toml`
- **KTS 引用修复**：转换为 TOML 后使用点号访问（如 `libs.hutool.all`）
- **死代码清理**：删除 6 个废弃文件，移除旧 Module Tasks 面板

### 🚀 Gradle 面板自动聚焦
- **自动跟随编辑器**：切换标签页时，右侧官方 Gradle 面板自动展开并聚焦到当前文件所属模块的 `Tasks > build` 节点
- 支持深层嵌套模块（如 `lib > gradle-plugin > conventions > spring-convention`）
- 仅在 Gradle 面板可见时触发，不影响性能

### 🎯 常用任务悬浮工具条
- 打开 `.gradle.kts` 或 `.gradle` 文件时，鼠标悬停编辑器顶部出现悬浮工具条
- 内置 12 个常用 Gradle 任务：clean、compileKotlin、build、test、jar、publishToMavenLocal、publishToMavenCentral、kspKotlin、kspCommonMainMetadata、signPlugin、publishPlugin、runIde
- 点击按钮即运行该任务，自动限定到当前文件所属模块（如 `:plugins:gradle-buddy:gradle-buddy-tasks:build`）
- **智能显隐**：根据当前模块类型自动隐藏不相关的任务
  - `kspCommonMainMetadata` 仅在 KMP（Kotlin Multiplatform）模块显示
  - `signPlugin` / `publishPlugin` / `runIde` 仅在 IntelliJ 插件开发模块显示
  - 通过检测 build script 中的插件标志（如 `intellijPlatform`、`kotlin("multiplatform")`）自动判断
- 每个任务有独立图标，方便快速识别
- 默认收藏列表可在 Settings → Tools → Gradle Buddy 中自定义

### ✨ 意图操作 (Alt+Enter)

本插件提供了一系列意图操作，让你在 `.gradle.kts` 和 `libs.versions.toml` 文件中更高效地管理依赖和插件。

所有意图操作都带有 `(Gradle Buddy)` 前缀，方便识别插件来源。

---

#### 意图操作汇总（按文件类型）

**.gradle.kts / settings.gradle.kts**

| 意图 | 说明 | 支持范围 |
| --- | --- | --- |
| Update dependency to latest version | 查询最新版本并替换 | 依赖与插件版本 |
| Convert dependency to version catalog format (TOML) | 硬编码依赖转为 TOML 引用 | 硬编码依赖 |
| Convert catalog reference to hardcoded dependency | `libs.xxx.yyy` 转硬编码 | 版本目录引用 |
| Select correct catalog reference | 智能修复无效引用 | 版本目录引用 |
| Browse catalog alternatives | 浏览并切换候选项 | 版本目录引用 |
| Select other versions | 选择指定版本并替换 | 硬编码依赖、版本目录引用 |
| Insert project dependency | 选择临近模块并插入 project 依赖 | dependencies 块 |
| Resolve plugin artifact for build-logic | 解析插件实现对应的预编译工件写入 TOML | plugins 块中的 `id("xxx")` |

**libs.versions.toml**

| 意图 | 说明 | 支持范围 |
| --- | --- | --- |
| Update dependency to latest version | 查询最新版本并替换 | [libraries] 依赖声明 |
| Update version variable to latest | 更新 [versions] 变量 | [versions] |
| Select other versions | 选择指定版本并替换 | [libraries] 依赖声明 |

**gradle-wrapper.properties**

| 意图 | 说明 | 支持范围 |
| --- | --- | --- |
| Update Gradle wrapper to latest | 更新 distributionUrl 为最新版本镜像 | distributionUrl 行 |

---

#### 在 `.gradle.kts` 或 `settings.gradle.kts` 文件中

将光标置于依赖或插件声明上，按下 `Alt+Enter`，即可触发以下操作：

**1. (Gradle Buddy) Update to latest version (更新到最新版本)**

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

**2. (Gradle Buddy) Convert to version catalog (转换为版本目录)**

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

**3. (Gradle Buddy) Select correct catalog reference (选择正确的版本目录引用)** 🆕

- **痛点**：版本目录引用写错了，但不知道 TOML 中正确的引用是什么。
- **解决**：使用智能相似度匹配，显示所有可能的候选项，按匹配度排序。

*示例*:
```kotlin
// 错误的引用
implementation(libs.com.google.devtools.ksp.gradle.plugin)
// 光标放在任意位置，按 Alt+Enter

// 显示候选项（按相似度排序）：
// 1. gradle.plugin.ksp [85%] (匹配: gradle, plugin, ksp)
// 2. ksp.gradle.plugin [75%] (匹配: ksp, gradle, plugin)
// 3. google.ksp [45%] (匹配: google, ksp)
```

**特性**：
- 🎯 **智能匹配**：使用多因子评分算法（完全匹配 50%、集合相似度 30%、顺序相似度 20%）
- 📊 **显示所有候选项**：不限制数量，显示所有有关键词匹配的别名
- 🔍 **光标位置无关**：无论光标在 `gradle`、`plugin` 还是 `ksp` 上，都能提取完整的 token 列表
- 📈 **匹配度显示**：每个候选项显示匹配百分比和匹配的关键词
- 🌐 **多模块支持**：递归扫描所有模块的 TOML 文件

**4. (Gradle Buddy) Browse catalog alternatives (浏览其他版本目录引用)** 🆕

**5. (Gradle Buddy) Select other versions (选择其他版本)** 🆕

- **痛点**：需要切换到指定版本，不一定是最新版本。
- **解决**：从 Maven Central 拉取历史版本列表并手动选择。

**6. (Gradle Buddy) Convert catalog reference to hardcoded dependency (转为硬编码)** 🆕

- **痛点**：临时测试或对比时，希望把 `libs.xxx.yyy` 转为硬编码。
- **解决**：自动解析 TOML 并替换为 `group:artifact:version`。

- **痛点**：想看看 TOML 中还有哪些相关的依赖可以用，但不想手动翻 TOML 文件。
- **解决**：即使当前引用有效，也可以浏览所有相关的候选项。

*示例*:
```kotlin
// 有效的引用
implementation(libs.gradle.plugin.ksp)
// 光标放在任意位置，按 Alt+Enter

// 显示所有相关候选项：
// 1. gradle.plugin.ksp [100%] ✓ 当前
// 2. ksp.gradle.plugin [85%] (匹配: ksp, gradle, plugin)
// 3. gradle.ksp [75%] (匹配: gradle, ksp)
```

**特性**：
- ✅ **当前引用标识**：用 "✓ 当前" 标记正在使用的引用
- 🔄 **快速切换**：轻松切换到其他版本或变体
- 🔍 **探索相关依赖**：发现 TOML 中所有包含相同关键词的依赖

---

#### 在 `libs.versions.toml` 文件中

将光标置于 TOML 文件中的任意位置，按下 `Alt+Enter`，即可触发以下操作：

**1. (Gradle Buddy) Update to latest version (更新到最新版本)**

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

**3. ⚠️ Normalize Version Catalog (规范化版本目录) — 危险操作，慎用**

> **警告**：Normalize 会修改整个项目中所有 `.gradle.kts` 文件的 `libs.xxx.yyy` 引用。这是一个项目级破坏性操作，请务必在执行前提交代码。

- **作用**：将 TOML 中的 alias 和 version.ref 统一重命名为 `groupId-artifactId` kebab-case 格式
- **影响范围**：
  - 重命名 `[libraries]` 中的 alias
  - 重命名 `[versions]` 中的 key（仅当该 key 只被一个 library 引用时）
  - 更新项目中所有 `.gradle.kts` 文件的 `libs.xxx.yyy` 引用
  - 自动修复因重命名产生的断裂引用（三级匹配策略）
- **安全措施**：
  - 点击 Normalize 后会弹出确认对话框，显示变更摘要（重命名数量和详情）
  - 用户确认后才执行，取消则不做任何修改
  - 建议在执行前 `git commit`，便于回滚

**3. (Gradle Buddy) Update version variable to latest (更新版本变量)** 🆕

- **痛点**：`[versions]` 中的变量需要逐个手动更新。
- **解决**：放在版本变量上按 `Alt+Enter`，自动更新到最新版本。

**4. (Gradle Buddy) Select other versions (选择其他版本)** 🆕

- **痛点**：需要从历史版本中选一个特定版本。
- **解决**：列出可用版本并替换 `version` 或 `version.ref`。

---

## 🏷️ 工件弃用管理

### 问题背景

项目中有些依赖已经不推荐使用（比如旧版 starter 被新版替代），但 TOML 里还留着，团队成员可能不知道哪些该避免使用。

### 解决方案

在 `libs.versions.toml` 的 `[libraries]` 区块中，每个工件旁边显示一个 Gradle 风格的绿色 gutter 图标。

**标记弃用**：
1. 右键点击工件旁边的图标
2. 选择「标记为弃用」
3. 输入弃用原因（可选）

标记后：
- TOML 中该工件的图标变为灰色 + 红色斜线
- 所有 `.gradle.kts` 文件中引用该工件的 `libs.xxx.yyy` 表达式显示删除线警告
- 悬停可查看弃用原因

**跨项目共享**：弃用元数据存储在 `~/.config/gradle-buddy/cache/deprecated-artifacts.json`，在 A 项目标记弃用后，B 项目也能看到警告。

**取消弃用**：右键已弃用工件的图标，选择「取消弃用」即可。

---

## 🔌 Build-Logic 插件工件解析

### 问题背景

在 `build-logic`（预编译脚本插件）中使用 Gradle 插件时，不能直接用 `id("xxx") version "yyy"`，而是需要在 `build-logic/build.gradle.kts` 中通过 `implementation(libs.xxx)` 引入插件的真实实现工件。但从 plugin id 找到对应的 `group:artifact` 并不直观。

### 解决方案

#### 1. Alt+Enter 意图操作

在任意 `.gradle.kts` 的 `plugins {}` 块中，将光标放在 `id("xxx")` 上按 Alt+Enter：

```kotlin
plugins {
    // 带版本 — 直接解析
    id("org.jetbrains.kotlin.jvm") version "2.0.0"

    // 不带版本（convention plugin 场景）— 自动查最新版本
    id("org.graalvm.buildtools.native")
}
```

插件会：
1. 通过 Plugin Marker Artifact 机制反查真实实现工件（优先 Gradle Plugin Portal，其次 Maven Central）
2. 无版本时自动查询 `maven-metadata.xml` 获取最新版本
3. 将工件写入 `libs.versions.toml` 的 `[versions]` 和 `[libraries]` 节

#### 2. 手动输入 fallback

自动解析失败时（私有仓库、网络问题等），弹出输入框支持两种格式：
- `group:artifact:version`（如 `org.graalvm.buildtools:native-gradle-plugin:0.10.4`）— 直接写入 TOML
- 纯版本号 — 继续走 marker 解析

#### 3. 批量操作

菜单 **Tools → Resolve All Plugin Artifacts for Build-Logic**：一键扫描所有 `.gradle.kts` 中带版本的插件声明，批量解析并写入 TOML。

---

### 📦 模块依赖快捷操作

#### Copy Module Dependency（右键菜单）

在编辑器或标签页上右键，选择「Copy Module Dependency」，自动将当前文件所属模块的依赖字符串复制到剪贴板：

```kotlin
implementation(project(":plugins:gradle-buddy:gradle-buddy-core"))
```

粘贴到另一个模块的 `dependencies {}` 块即可引入。根模块（`:`）时不显示此菜单。

#### Insert Project Dependency（Alt+Enter 意图操作）

在 `.gradle.kts` 的 `dependencies {}` 块内按 Alt+Enter，选择「Insert project dependency」：

- 弹出项目所有模块列表
- 按目录树距离排序（基于 LCA 算法），距离越近排越前
- 每个候选项显示距离指标，如 `gradle-buddy-core [↕2]`
- 选择后自动插入 `implementation(project(":path"))` 到当前行下方

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

## 一键迁移 Project 级别依赖到 Maven 中央仓库发布过的依赖(一般是库作者会使用,模块解耦)

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

### 🛠️ Gradle 面板集成

- **自动聚焦**：切换编辑器标签页时，官方 Gradle 面板自动展开并选中当前模块的 `Tasks > build`

---

## 🔧 Plugin ID 修复工具

### 问题背景

当你在 `build-logic` 中定义预编译脚本插件时，Gradle 要求使用完全限定的插件 ID：

```kotlin
// build-logic/src/main/kotlin/com/example/my-plugin.gradle.kts
plugins {
    `java-library`
}

// ❌ 错误：使用短 ID 会导致运行时失败
plugins {
    id("my-plugin")
}

// ✅ 正确：必须使用完全限定 ID
plugins {
    id("com.example.my-plugin")
}
```

这个问题很难调试，因为 IDE 不会报错，只有在运行时才会失败。

### 解决方案

#### 1. 快速修复 (Alt+Enter)

将光标放在短插件 ID 上，按 `Alt+Enter`，选择 **"Fix build-logic qualified name"**：

- 自动扫描 `build-logic` 目录
- 提取插件的包名
- 在整个项目中替换所有该插件的短 ID 引用

#### 2. 批量修复

使用菜单 **Tools → Fix All Plugin IDs in Project**：

1. 扫描所有 `build-logic` 目录
2. 找到所有预编译脚本插件
3. 检测项目中所有短 ID 引用
4. 一键替换为完全限定 ID

### 功能特性

- ✅ 自动扫描 `build-logic` 目录结构
- ✅ 从 Kotlin 文件提取包名
- ✅ 检测 `plugins {}` 块中的插件引用
- ✅ 项目范围的批量替换
- ✅ 线程安全的 PSI 访问
- ✅ 进度指示器和详细通知

### 使用示例

**修复前**：
```kotlin
// build.gradle.kts
plugins {
    id("java-library")  // ❌ 短 ID
    id("spring-conventions")  // ❌ 短 ID
}
```

**修复后**：
```kotlin
// build.gradle.kts
plugins {
    id("com.example.conventions.java-library")  // ✅ 完全限定
    id("com.example.conventions.spring-conventions")  // ✅ 完全限定
}
```

---

## 快捷键汇总

| 快捷键 | 功能 |
|-------|------|
| `Alt+Enter` | 在依赖上触发意图操作（更新版本等） |
| `Alt+Enter` | 在插件 ID 上触发快速修复（修复为完全限定名） |

---

## 🔄 Gradle Wrapper 镜像更新

### 问题背景

国内开发者下载 Gradle 分发包速度慢，每个项目的 `gradle-wrapper.properties` 都需要手动改镜像地址，多项目时更新版本也很麻烦。

### 解决方案

#### 1. 启动自动检查

项目打开时自动检测所有 `gradle-wrapper.properties` 的 Gradle 版本，如果不是最新版则弹出通知：
- 一键更新按钮（使用设置中的首选镜像）
- "Choose Mirror..." 按钮打开完整镜像选择

#### 2. Tools 菜单操作

**Tools → Update Gradle Wrapper (Mirror)**：
- 扫描项目中所有 `gradle-wrapper.properties`（支持多模块）
- 显示每个文件的当前版本和最新版本
- 提供腾讯云 / 阿里云 / 官方三个镜像按钮，一键批量更新

#### 3. Alt+Enter 意图操作

在 `gradle-wrapper.properties` 文件中，光标在 `distributionUrl=` 行上按 Alt+Enter：
- 弹出镜像选择菜单
- 选择后就地替换 URL
- 保持原有的分发类型（bin/all）

#### 4. 首选镜像设置

**Settings → Tools → Gradle Buddy → Gradle Wrapper preferred mirror**：
- 设置默认镜像，启动检查和一键更新都会使用此镜像
- 可选：Tencent Cloud（腾讯云）、Aliyun（阿里云）、Gradle Official

#### 5. 自动更新模式

**Settings → Tools → Gradle Buddy → Auto-update Gradle Wrapper on project open**：
- 勾选后，每次打开项目自动检查并静默更新所有 `gradle-wrapper.properties` 到最新版本
- 使用上方设置的首选镜像
- 更新完成后显示简短通知，无需任何手动操作
- 未勾选时保持原有行为（弹出交互通知，手动点击更新）

### 支持的镜像

| 镜像 | URL 模板 |
|------|----------|
| 腾讯云 | `https://mirrors.cloud.tencent.com/gradle/gradle-{version}-{type}.zip` |
| 阿里云 | `https://mirrors.aliyun.com/macports/distfiles/gradle/gradle-{version}-{type}.zip` |
| 官方 | `https://services.gradle.org/distributions/gradle-{version}-{type}.zip` |

---

## 后续计划
- [ ] 模块白名单/黑名单
- [ ] 依赖冲突检测和解决建议
- [ ] Plugin ID 验证和自动补全
- [ ] 支持 Groovy DSL 的插件 ID 修复

---

## Tips

**模块睡眠功能已迁移**：模块睡眠功能已迁移到独立的 **Gradle Module Sleep** 插件，提供更专业的按需加载和自动睡眠管理。

如果你需要模块按需加载和自动睡眠功能，建议使用 **Gradle Module Sleep** 插件。

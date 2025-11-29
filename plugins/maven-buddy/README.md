Maven Buddy is an IntelliJ IDEA plugin that helps you quickly search and add Maven dependencies from Maven Central directly within your IDE. This plugin streamlines the process of finding and managing dependencies for your Java and Kotlin projects.

快速从 Maven Central 搜索和添加依赖的 IntelliJ IDEA 插件。

## ✨ 功能特性

### 搜索功能
- 🔍 **快速搜索**: 按两下 Shift 打开搜索，直接搜索 Maven 依赖
- 📋 **一键复制**: 选择依赖后自动复制到剪贴板
- 🎯 **智能格式**: 自动检测项目类型（Maven/Gradle Kotlin/Gradle Groovy），复制时使用对应格式
- ⚡ **智能搜索**: 支持按 groupId、artifactId 或关键词搜索
- 🔎 **精确匹配**: 使用 `:` 分隔符进行精确坐标搜索

### 历史与缓存
- 📜 **搜索历史**: 记录使用过的依赖，下拉快速选择（按 groupId:artifactId 去重）
- 🌐 **全局共享**: 历史记录和缓存跨项目共享，切换项目后数据仍然保留
- 📁 **可配置路径**: 存储路径可在设置中分别自定义
  - 历史记录默认: `~/.config/maven-buddy/history.json`
  - 搜索缓存默认: `~/.config/maven-buddy/cache.json`
- 💾 **持久化缓存**: 搜索结果缓存 7 天，避免重复调用 API
- 📊 **分组显示**: 历史(📜)、缓存(💾)、搜索(🔍) 三种来源明确区分
- ⏱️ **时间排序**: 搜索结果按更新时间降序排列

### 翻页与加载
- 📄 **分页加载**: 支持滚动加载更多结果（默认每页 50 条）
- 🔄 **增量加载**: 滚动到底部自动加载下一页

### Version Catalog 支持
- 📝 **TOML 补全**: 在 `libs.versions.toml` 中智能补全依赖
- 🔄 **批量迁移**: 一键将项目中所有硬编码依赖迁移到 Version Catalog

## 📦 安装

### 从源码构建

```bash
cd /Users/zjarlin/IdeaProjects/autoddl-idea-plugin
./gradlew :plugins:maven-buddy:buildPlugin
```

生成的插件位于：`plugins/maven-buddy/build/distributions/`

### 安装到 IDE

1. 打开 IntelliJ IDEA
2. 进入 `Settings → Plugins`
3. 点击齿轮图标 → `Install Plugin from Disk...`
4. 选择生成的 `.zip` 文件

## 🚀 使用方法

### 1. 打开搜索

按 **`Shift + Shift`** 打开 Search Everywhere

### 2. 切换到 Maven Dependencies 标签

在搜索窗口顶部，点击 **`Maven Dependencies`** 标签

### 3. 输入搜索关键词

**支持三种搜索方式**：

#### 方式1: 关键词搜索
```
spring-boot
guice
jackson
```

#### 方式2: GroupId 搜索
```
com.google.inject
org.springframework.boot
```

#### 方式3: 精确坐标搜索
```
com.google.inject:guice
org.springframework.boot:spring-boot-starter
```

### 4. 选择依赖

点击搜索结果或按 `Enter` 键，依赖声明将自动复制到剪贴板

## ⚙️ 配置

进入 `Settings → Tools → Maven Buddy` 进行配置：

### 依赖格式（自动检测）

插件会根据项目文件自动检测构建类型并选择对应的复制格式：

| 检测文件 | 格式 |
|---------|------|
| `build.gradle.kts` / `settings.gradle.kts` | Gradle Kotlin DSL |
| `build.gradle` / `settings.gradle` | Gradle Groovy DSL |
| `pom.xml` | Maven XML |
| 无构建文件 | Gradle Kotlin DSL (默认) |

**格式示例：**

```kotlin
// Gradle Kotlin DSL
implementation("com.google.inject:guice:5.1.0")
```

```groovy
// Gradle Groovy DSL
implementation 'com.google.inject:guice:5.1.0'
```

```xml
<!-- Maven XML -->
<dependency>
    <groupId>com.google.inject</groupId>
    <artifactId>guice</artifactId>
    <version>5.1.0</version>
</dependency>
```

### 基本配置

- **最大搜索结果数**: 1-100（默认 20）
- **自动复制到剪贴板**: 选择后自动复制（默认启用）
- **搜索超时**: 1-60 秒（默认 10 秒）

### 全局存储配置

存储路径可在设置中分别自定义，数据全局存储，所有项目共享：

| 数据类型 | 默认路径 |
|---------|---------|
| 历史记录 | `~/.config/maven-buddy/history.json` |
| 搜索缓存 | `~/.config/maven-buddy/cache.json` |

- **重置按钮**: 每个路径配置项旁都有 "Reset" 按钮恢复默认路径
- **JSON 格式**: 便于查看、编辑和备份

### 搜索行为（Search Behavior）⚡

#### 1. 防抖延迟（Debounce Delay）
- **默认值**: 500 毫秒
- **推荐值**:
  - **300ms** - 快速响应，适合快速输入
  - **500ms** - 平衡选项（推荐）
  - **800ms** - 减少请求，适合慢速网络
- **作用**: 输入停止后等待多久才触发搜索
- **范围**: 100-2000 毫秒

#### 2. 手动触发模式
- **选项**: "Require Enter key to trigger search"
- **默认**: 关闭（自动搜索）
- **启用后**: 必须按 Enter 键才触发搜索
- **适用场景**:
  - 想完全控制搜索时机
  - 避免输入过程中的网络请求
  - 网络环境不稳定

**详细说明**: 参见 [DEBOUNCE_CONFIG.md](DEBOUNCE_CONFIG.md)

## 📖 示例

### 示例 1: 搜索 Spring Boot
```
搜索关键词: spring-boot
```

结果：
```
org.springframework.boot:spring-boot-starter
org.springframework.boot:spring-boot-starter-web
org.springframework.boot:spring-boot-starter-data-jpa
...
```

### 示例 2: 精确搜索 Guice
```
搜索关键词: com.google.inject:guice
```

结果：
```
com.google.inject:guice
Version: 7.0.0 | Repo: central | Type: jar
```

选择后复制（Gradle Kotlin 格式）：
```kotlin
implementation("com.google.inject:guice:7.0.0")
```

### 示例 3: 搜索 Jackson
```
搜索关键词: jackson
```

结果：
```
com.fasterxml.jackson.core:jackson-databind
com.fasterxml.jackson.core:jackson-core
com.fasterxml.jackson.core:jackson-annotations
...
```

## 🔄 Version Catalog 迁移

### 批量迁移

将项目中所有硬编码依赖迁移到 `libs.versions.toml`：

**入口**:
- `Tools` 菜单 → `Migrate Dependencies to Version Catalog`
- 项目右键 → `Migrate Dependencies to Version Catalog`

**转换示例**:
```kotlin
// 迁移前 (build.gradle.kts)
implementation("com.google.guava:guava:32.1.3-jre")
implementation("com.fasterxml.jackson.core:jackson-core:2.15.0")

// 迁移后 (build.gradle.kts)
implementation(libs.guava)
implementation(libs.jackson.core)
```

```toml
# 生成的 gradle/libs.versions.toml
[versions]
guava = "32.1.3-jre"
jackson = "2.15.0"

[libraries]
guava = { group = "com.google.guava", name = "guava", version.ref = "guava" }
jackson-core = { group = "com.fasterxml.jackson.core", name = "jackson-core", version.ref = "jackson" }
```

### TOML 文件补全

在 `*.versions.toml` 文件中输入时自动补全：

```toml
[libraries]
# 输入 "guava" 后触发补全
guava = "com.google.guava:guava:32.1.3-jre"

# 支持多种格式
jackson = { module = "com.fasterxml.jackson.core:jackson-core", version = "2.15.0" }
spring = { group = "org.springframework", name = "spring-core", version = "6.1.0" }
```

## 🔧 技术栈

- **搜索 API**: Maven Central REST API
- **网络请求**: OkHttp + CurlExecutor
- **JSON 解析**: Jackson
- **UI 框架**: IntelliJ Platform SDK

## 🎯 工作原理

1. **搜索触发**: 用户在 Search Everywhere 中输入关键词
2. **API 调用**: 使用 `MavenCentralSearchUtil` 调用 Maven Central REST API
3. **结果展示**: 使用自定义 `ListCellRenderer` 展示搜索结果
4. **复制操作**: 根据设置格式化依赖声明并复制到剪贴板

## 📁 项目结构

```
maven-buddy/
├── src/main/kotlin/
│   └── site/addzero/maven/search/
│       ├── MavenDependencySearchContributor.kt  # Search Everywhere 贡献者
│       ├── MavenArtifactCellRenderer.kt         # 列表渲染器（分组显示）
│       ├── settings/
│       │   ├── MavenSearchSettings.kt           # 设置持久化
│       │   └── MavenSearchConfigurable.kt       # 设置页面
│       ├── history/
│       │   └── SearchHistoryService.kt          # 搜索历史服务
│       ├── cache/
│       │   └── SearchResultCacheService.kt      # 搜索结果缓存服务
│       ├── completion/
│       │   ├── GradleKtsCompletionContributor.kt    # Gradle KTS 补全
│       │   └── VersionCatalogCompletionContributor.kt # TOML 补全
│       └── migration/
│           └── MigrateToVersionCatalogAction.kt # 批量迁移 Action
├── src/main/resources/
│   └── META-INF/
│       └── plugin.xml                            # 插件描述文件
└── build.gradle.kts                              # 构建配置
```

## 🔗 相关链接

- [Maven Central REST API 文档](https://central.sonatype.org/search/rest-api-guide/)
- [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/welcome.html)
- [Search Everywhere API](https://plugins.jetbrains.com/docs/intellij/search-everywhere.html)


## 📄 许可证

与主项目相同

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📮 反馈

如有问题或建议，请在 GitHub 上创建 Issue。

---

**享受快速搜索 Maven 依赖的乐趣！** 🎉

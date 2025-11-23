# Maven Dependency Search Plugin

快速从 Maven Central 搜索和添加依赖的 IntelliJ IDEA 插件。

## ✨ 功能特性

- 🔍 **快速搜索**: 按两下 Shift 打开搜索，直接搜索 Maven 依赖
- 📋 **一键复制**: 选择依赖后自动复制到剪贴板
- ⚙️ **格式可配置**: 支持 Maven XML、Gradle Kotlin DSL、Gradle Groovy DSL 三种格式
- ⚡ **智能搜索**: 支持按 groupId、artifactId 或关键词搜索
- 🎯 **精确匹配**: 使用 `:` 分隔符进行精确坐标搜索

## 📦 安装

### 从源码构建

```bash
cd /Users/zjarlin/IdeaProjects/autoddl-idea-plugin
./gradlew :plugins:maven-search-plugin:buildPlugin
```

生成的插件位于：`plugins/maven-search-plugin/build/distributions/`

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

进入 `Settings → Tools → Maven Search` 进行配置：

### 依赖格式

选择复制依赖时使用的格式：

#### Maven XML
```xml
<dependency>
    <groupId>com.google.inject</groupId>
    <artifactId>guice</artifactId>
    <version>5.1.0</version>
</dependency>
```

#### Gradle Kotlin DSL (推荐)
```kotlin
implementation("com.google.inject:guice:5.1.0")
```

#### Gradle Groovy DSL
```groovy
implementation 'com.google.inject:guice:5.1.0'
```

### 其他配置

- **最大搜索结果数**: 1-100（默认 20）
- **自动复制到剪贴板**: 选择后自动复制（默认启用）
- **搜索超时**: 1-60 秒（默认 10 秒）

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
maven-search-plugin/
├── src/main/kotlin/
│   └── site/addzero/maven/search/
│       ├── MavenDependencySearchContributor.kt  # Search Everywhere 贡献者
│       ├── MavenArtifactCellRenderer.kt         # 列表渲染器
│       ├── settings/
│       │   ├── MavenSearchSettings.kt           # 设置持久化
│       │   └── MavenSearchConfigurable.kt       # 设置页面
│       └── util/                                 # 工具类（临时复制）
│           ├── MavenCentralSearchUtil.kt
│           ├── CurlExecutor.kt
│           └── CurlParser.kt
├── src/main/resources/
│   └── META-INF/
│       └── plugin.xml                            # 插件描述文件
└── build.gradle.kts                              # 构建配置
```

## 🔗 相关链接

- [Maven Central REST API 文档](https://central.sonatype.org/search/rest-api-guide/)
- [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/welcome.html)
- [Search Everywhere API](https://plugins.jetbrains.com/docs/intellij/search-everywhere.html)

## 📝 开发说明

### 依赖工具类

插件使用了 `site.addzero:tool-api-maven:2025.11.27` 工具类进行 Maven Central 搜索。

```kotlin
// 关键词搜索（类似单测用法）
MavenCentralSearchUtil.searchByKeyword("jackson", 5)

// GroupId 搜索
MavenCentralSearchUtil.searchByGroupId("com.google.inject", 20)

// 精确坐标搜索
MavenCentralSearchUtil.searchByCoordinates("com.google.inject", "guice", 20)

// 获取最新版本
MavenCentralSearchUtil.getLatestVersion("com.google.inject", "guice")
```

### 构建插件

```bash
./gradlew :plugins:maven-search-plugin:buildPlugin
```

### 运行测试 IDE

```bash
./gradlew :plugins:maven-search-plugin:runIde
```

## 📄 许可证

与主项目相同

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📮 反馈

如有问题或建议，请在 GitHub 上创建 Issue。

---

**享受快速搜索 Maven 依赖的乐趣！** 🎉

# Changelog

All notable changes to Gradle Buddy plugin will be documented in this file.

## [2026.02.18] - 2026-02-18

### ✨ 新增功能
- **内置搜索模块 (gradle-buddy-search)**：将 maven-buddy-core 的搜索/缓存/历史功能迁移到 gradle-buddy 内部独立模块
  - `SearchResultCacheService`：SQLite 本地缓存，加速重复搜索
  - `SearchHistoryService`：搜索历史持久化（关键词 + 选中工件）
  - `MavenSearchSettings`：搜索配置（分页、超时、防抖等）
  - 安装 gradle-buddy 即可使用智能补全，无需额外安装 maven-buddy

### 🗑️ 移除
- **MavenBuddyBridge**：删除反射桥接层，补全功能直接引用 gradle-buddy-search 模块的服务类
  - 彻底消除 `Package 'site.addzero.maven' is not found` 插件验证错误
  - 不再需要 `compileOnly` maven-buddy-core 依赖

### 🔧 改进
- **插件验证通过**：字节码中不再包含对 `site.addzero.maven.*` 的任何引用
- **补全性能**：直接调用服务替代反射，减少运行时开销

## [2026.02.16] - 2026-02-16

### ✨ 新增功能
- **Wrapper 自动更新**：Settings → Tools → Gradle Buddy 新增「Auto-update Gradle Wrapper on project open (自动更新 Wrapper)」复选框
  - 启用后，每次打开项目时自动检查并静默更新所有 `gradle-wrapper.properties` 到最新版本
  - 使用首选镜像（腾讯云/阿里云/官方），无需手动操作
  - 更新完成后显示简短通知（如 "Auto-updated 2 wrapper(s) to Gradle 9.3.1, Mirror: Tencent Cloud"）
  - 未启用时保持原有行为（弹出交互通知，手动点击更新）

### 🐛 修复
- **gradle-buddy-wrapper 模块未打包**：修复 `ClassNotFoundException: WrapperVersionCheckStartup`，在主模块 `build.gradle.kts` 中补充 `implementation(project(":plugins:gradle-buddy:gradle-buddy-wrapper"))` 依赖

## [2026.02.11] - 2026-02-11

### ✨ 新增功能
- **Gradle Wrapper 镜像更新 (gradle-buddy-wrapper)**：新子模块，一站式管理 Gradle Wrapper 版本和镜像
  - `UpdateGradleWrapperAction`：Tools 菜单操作，扫描所有 `gradle-wrapper.properties`，显示版本对比，提供腾讯云/阿里云/官方三个镜像按钮一键批量更新
  - `UpdateWrapperIntention`：Alt+Enter 意图操作，在 `gradle-wrapper.properties` 的 `distributionUrl=` 行上触发，弹出镜像选择器就地替换
  - `WrapperVersionCheckStartup`：项目启动时自动检测 wrapper 版本，过期则通知提醒，一键更新使用首选镜像
  - `GradleWrapperUpdater`：核心工具类，通过 Gradle Services API 获取最新版本，支持 3 个镜像模板，递归查找 wrapper 文件
  - Settings → Tools → Gradle Buddy 新增「Gradle Wrapper preferred mirror」下拉框，设置默认镜像
  - 意图操作移除 `<language>Properties</language>` 限制，改为 `isAvailable()` 中检查文件名，兼容性更好
- **Create Bundle 意图操作**：选中多行 `implementation(libs.xxx)` 依赖，Alt+Enter 一键创建 `[bundles]` 条目
  - 自动提取选中行中的 `libs.xxx.yyy` 别名
  - 弹出输入框命名 bundle，默认基于公共前缀推断
  - 同名 bundle 已存在时自动合并（追加新别名，去重）
  - 写入 TOML 后自动将选中行替换为 `implementation(libs.bundles.xxx)`
- **Unbundle 意图操作**：光标在 `implementation(libs.bundles.xxx)` 上 Alt+Enter，一键展开为独立依赖行
  - 从 TOML `[bundles]` 中读取 bundle 成员列表
  - 替换当前行为多行 `implementation(libs.xxx)` 声明，保持缩进
- **仓库探测与自动添加 (RepositoryProber)**：Maven Central 找不到的依赖，自动探测 8 个常见仓库
  - Google Maven、JitPack、Gradle Plugin Portal、JetBrains Compose、Sonatype Snapshots/s01、JetBrains Maven、Kotlin Wasm Experimental
  - 通过 HTTP HEAD 请求检查 POM 是否存在
  - 通知中显示 "Add {RepoName}" 按钮，一键添加仓库声明
  - 智能插入位置：settings.gradle.kts `dependencyResolutionManagement` → 根 build.gradle.kts `repositories` → 模块 build.gradle.kts
  - 自动检测已有仓库声明，避免重复添加
  - Fix All 批量修复也支持仓库探测
- **硬编码依赖转 TOML 意图操作 (GradleKtsHardcodedDependencyToTomlIntention)**：在 `.gradle.kts` 中对硬编码依赖 Alt+Enter 转为版本目录引用
  - 支持 `group:artifact:version`、`group:artifact:version@classifier`、`group:artifact:version:extension@classifier` 格式
  - 自动检测 TOML 中已有相同版本号的 `[versions]` 条目，弹出选择复用或新建
  - 已有同坐标 library 时复用其 alias 和 version.ref
  - alias 冲突时自动追加 groupId 后缀消歧
  - 就地更新 TOML（upsert [versions] 和 [libraries]），不覆盖已有内容

### 🔧 改进
- **版本目录引用过滤**：library 引用修复时过滤掉 `libs.versions.xxx` 候选项（仅保留 library 类型候选）
  - 过滤后仅剩 1 个候选时静默替换，无需弹窗
  - 同时应用于 `SelectCatalogReferenceIntentionGroup` 和 `BrowseCatalogAlternativesIntention`
- **替换逻辑防双 libs**：修复替换时可能产生 `libs.libs.xxx` 的问题，使用安全的文本替换方法
- **maven-buddy 独立性**：`gradle-buddy-intentions` 对 `maven-buddy-core` 和 `tool-api-maven` 使用 `compileOnly`，通过 `MavenBuddyBridge` 运行时桥接，彻底消除 ClassLoader 冲突
- **settings.gradle.kts 检测**：`GradleModuleSleepService` 使用 `GradleSettings.linkedProjectsSettings` 获取真实 Gradle 根路径，不再依赖不可靠的 `project.basePath`
- **统一 TOML 文件解析**：所有文件统一使用 `GradleBuddySettingsService.resolveVersionCatalogFile(project)` 解析版本目录文件路径
  - 涉及 12+ 个文件：GradleKtsPluginToAliasIntention、GradleKtsPluginToTomlIntention、FixUnresolvableDependenciesAction、VersionCatalogFloatingToolbarProvider、DeprecatedArtifactInspection、ResolvePluginArtifactIntention、ResolveAllPluginArtifactsAction、MigrateToVersionCatalogAction、VersionCatalogDependencyHelper 等
- **Gradle 错误格式兼容**：`parseUnresolvedDependencies` 预处理 `.Required by:` 和 `xxxRequired by:` 拼接格式，自动拆行后再解析
- **FixBrokenCatalogReferencesAction**：新增 `filterCandidatesForLibraryRef()` 过滤 versions 候选、`MergedBrokenRef` 分组、ComboBox 渲染器修复双 libs

### 🐛 修复
- 修复 `Could not find xxx.Required by:project ':yyy'` 无换行导致解析失败的问题
- 修复 `ResolvePluginArtifactIntention` 中 `$catalogPath` 悬空变量引用
- 修复 `MigrateToVersionCatalogAction` 中未使用的 `basePath`/`catalogPath` 变量
- 修复 `VersionCatalogDependencyHelper` 中冗余的 `catalogPath` 和 `ioFile` 别名

## [2026.02.10] - 2026-02-10

### ✨ 新增功能
- **Gradle Sync 依赖解析错误自动捕获**：Gradle Sync（项目导入/刷新）时遇到 "Could not find/resolve" 依赖错误，自动弹出通知提供一键修复
  - 新增 `onStatusChange` 捕获通道：Gradle Sync 的依赖解析错误通过 `ExternalSystemTaskNotificationEvent.description` 传递，格式为 `Could not resolve group:artifact:version for :module:sourceSet`
  - 新增 `forModulePattern` 正则：从 `for :module:path:sourceSet` 后缀中提取报错模块路径
  - 修复 `onSuccess` 过早清除 buffer 的问题：Gradle Sync 即使有 dependency resolution warnings 也会触发 `onSuccess`，之前它把 `outputBuffers` 清掉了导致 `onEnd` 拿不到数据
  - 增强 `onFailure` exception chain 遍历：加入 `visited` 集合防循环，遍历 `suppressed` exceptions（Gradle 有时把多个错误放在 suppressed 里）
  - `outputBuffers` 从 `mutableMapOf` 改为 `ConcurrentHashMap`，防止并发问题
  - 新增 `processedTasks` 集合防止 `onFailure` 和 `onEnd` 重复处理同一个 task
  - `taskPrefixPattern` 正则修复：`^> ?` → `^>?\s*:?`，支持无 `>` 前缀的错误行格式
  - `depPattern` 版本号匹配修复：`([^\s.]+(?:\.[^\s.]+)*)` → `([^\s.,;)]+)`，修复 `1.7` 等短版本号的边界匹配问题
  - Required by 扫描范围从 10 行扩大到 15 行，空行后继续检查下一行是否为 Required by
- **智能依赖补全 (KTS)**：在 `.gradle.kts` 的 `dependencies {}` 块中输入关键字，自动搜索 Maven Central 并补全
  - 三种输入模式：`implementation("xxx`（引号内）、`implementation(xxx`（无引号）、裸输入（直接输入关键字自动包裹 `implementation("...")`）
  - KMP 项目支持：`commonMainImplementation`、`iosMainApi` 等 sourceSet 配置
  - 静默 upsert TOML 模式：开启后自动写入 `libs.versions.toml` 并回显 `implementation(libs.xxx.xxx)`
  - 补全优先级置顶（`order="FIRST"` + 高优先级值），Gradle Buddy 建议始终排在最前
  - 历史记录 / 缓存 / Maven Central 三级搜索
- **智能依赖补全 (TOML)**：在 `libs.versions.toml` 的 `[libraries]` 部分输入关键字，自动搜索并补全
  - 值补全：在引号内输入 `groupId:artifactId` 自动搜索
  - 裸 alias 输入：在 `[libraries]` 下直接输入 alias 关键字（如 `jimmer-ksp`），自动生成完整声明行
  - 同 group 智能复用：已有 `jimmer-sql-kotlin` 时输入 `jimmer-ksp`，自动复用 `version.ref = "jimmer"`
  - alias 命名规则：`artifactId` kebab-case，冲突时加 `groupId-` 前缀（不拼 version 后缀）
  - 自动在 `[versions]` 中插入版本条目
- **最新版本保证**：补全选中后，后台调用 `MavenCentralSearchUtil.getLatestVersion()` 获取真正最新版本
  - 版本比较保护：resolved 版本必须 >= 搜索版本，绝不降级
  - 异步替换：先用搜索版本即时插入（无延迟），后台获取最新版本后自动替换
- **Settings 新增选项**：`Tools → Gradle Buddy` 新增「Smart completion: silent upsert to TOML」复选框

### 🔧 改进
- **Normalize 二次确认弹窗**：点击 Normalize 不再直接执行，而是弹出确认对话框
  - 显示将要执行的操作摘要（TOML 重命名 + KTS 引用更新）
  - 列出前 10 个 alias 重命名详情
  - 警告用户这是项目级破坏性操作，建议先提交代码

## [2026.02.09-3] - 2026-02-09

### ✨ 新增功能
- **id("...") 截断引用修复 (ConvertPluginIdToAliasIntention)**：类似 `SelectCatalogReferenceIntentionGroup` 对 `libs.xxx` 断裂引用的修复，但作用于 `plugins {}` 块中的 `id("...")` 声明
  - 在 `id("koin.compiler")` 上 Alt+Enter，通过 TOML `[plugins]` 的 `id` 字段匹配找到正确条目
  - 替换为 `alias(libs.plugins.xxx)`
  - 支持精确匹配、标准化匹配（`.` 与 `-` 互换）、后缀匹配、token 交集模糊匹配
  - 单候选直接替换，多候选弹出选择菜单
  - 仅处理无 `version` 后缀的 `id("...")`（有 version 的由 `GradleKtsPluginToAliasIntention` 处理）
- **CatalogReferenceScanner.scanPluginIdToAlias()**：新增方法，从 TOML `[plugins]` 提取 plugin ID → accessor 映射

### 🔧 改进
- **CatalogReferenceScanner**：清理未使用的 `FileTypeIndex`、`GlobalSearchScope`、`TomlLiteralKind` 导入

## [2026.02.09-2] - 2026-02-09

### ✨ 新增功能
- **断裂引用修复 (FixBrokenCatalogReferencesAction)**：独立操作，扫描所有 `.gradle.kts` 文件并修复断裂的版本目录引用
  - 单候选自动修复，多候选弹出表格对话框（ComboBox 下拉选择）
  - 零候选标记为无法修复，汇总展示
  - 注册在版本目录悬浮工具条和 Tools 菜单中
- **Normalize 二次校验**：`NormalizeVersionCatalogAction` 在重命名后增加第三步——重新扫描所有 `.gradle.kts`，自动修复因重命名产生的断裂引用
  - 三级匹配策略：精确归一化匹配 → token 后缀匹配 → token 有序子集匹配

### 🐛 修复
- **误报过滤**：排除以下不应被识别为版本目录引用的 case
  - JVM 反射链：`libs.javaClass.superclass.protectionDomain.codeSource.location`
  - 动态 API 调用：`libs.findLibrary("xxx").get()`、`libs.findBundle()`、`libs.findPlugin()`、`libs.findVersion()`
  - `settings.gradle.kts` 中的 `versionCatalogs { create("libs") { ... } }` 声明块
  - 字符串字面量内的匹配（如 `from(files("../gradle/libs.versions.toml"))`）
- **`[versions]` / `[bundles]` 引用识别**：之前只注册了 `[libraries]` 和 `[plugins]` 的 accessor，导致 `libs.versions.android.compileSdk.get()` 等合法引用被误判为断裂。现在四个 section 全部注册
- **Provider API 方法剥离**：regex 会捕获尾部的 `.get`、`.getOrNull`、`.asProvider` 等 Gradle Provider API 方法名，现在自动剥离后再匹配
- **双 catalog 前缀修复**：`libs.libs.com.google.devtools.ksp...` 这种重复 catalog 名的引用，自动剥离多余前缀后精确匹配
- **全部无候选时静默**：当所有断裂引用都无候选项时，不再弹出空表格对话框，直接显示摘要
- **`List.indexOf` 编译错误**：`isOrderedSubset` 中 `List<String>.indexOf()` 不支持 `startIndex` 参数，改用 `subList` 实现

## [2026.02.09] - 2026-02-09

### ✨ 新增功能
- **Copy Module Dependency**：编辑器右键菜单 / 标签页右键菜单新增「Copy Module Dependency」，一键复制当前文件所属模块的 `implementation(project(":path"))` 到剪贴板
- **Insert Project Dependency**：在 `.gradle.kts` 的 `dependencies {}` 块内 Alt+Enter，弹出项目所有模块列表
  - 按目录树距离排序（基于 LCA 算法），距离越近排越前
  - 每个候选项显示短名称和距离指标，如 `gradle-buddy-core [↕2]`
  - 选择后自动插入 `implementation(project(":path"))` 到当前行下方，保持缩进
- **常用任务悬浮工具条**：在 `.gradle.kts` / `.gradle` 文件中，鼠标悬停编辑器顶部显示悬浮工具条，一键运行常用 Gradle 任务
  - 任务自动限定到当前编辑器文件所属模块（如 `:plugins:gradle-buddy:gradle-buddy-tasks:build`）
  - 内置 12 个常用任务：clean、compileKotlin、build、test、jar、publishToMavenLocal、publishToMavenCentral、kspKotlin、kspCommonMainMetadata、signPlugin、publishPlugin、runIde
  - **智能显隐**：`kspCommonMainMetadata` 仅在 KMP 模块显示，`signPlugin` / `publishPlugin` / `runIde` 仅在 IntelliJ 插件开发模块显示
  - 通过检测 build script 中的插件标志自动判断模块类型（如 `intellijPlatform`、`buildlogic.intellij.`、`kotlin("multiplatform")`）
  - 每个任务有独立图标，方便快速识别
- **Build-Logic 插件工件解析**：
  - **Alt+Enter 意图操作**：在 `plugins {}` 块中对 `id("xxx")` 按 Alt+Enter，解析插件的真实实现工件坐标并写入 TOML
  - **支持无版本声明**：convention plugin 中 `id("xxx")` 不带 version 时，自动查询最新版本
  - **手动输入 fallback**：自动解析失败时弹出输入框，支持 `group:artifact:version` 格式直接写入
  - **批量操作**：Tools 菜单新增「Resolve All Plugin Artifacts for Build-Logic」
- **Gradle 面板自动聚焦**：切换编辑器标签页时，右侧官方 Gradle 面板自动展开并聚焦到当前文件所属模块的 `Tasks > build` 节点
  - 支持深层嵌套模块路径
  - 仅在 Gradle 面板可见时触发，不影响性能

### 🐛 修复
- **Normalize 重复 alias**：修复同一 `groupId:artifactId` 不同版本时 alias 冲突的问题
  - 三级去重策略：artifactId → groupId-artifactId → groupId-artifactId-vVersion

### 🗑️ 清理
- 移除旧的 "Module Tasks" 自定义工具窗口，改为直接操控官方 Gradle 面板

## [2026.02.08] - 2026-02-08

### ✨ 新增功能
- **工件弃用管理 (Line Marker)**：`libs.versions.toml` 的 `[libraries]` 区块中，每个工件旁边显示 Gradle 风格的 gutter 图标
  - 右键图标可将工件标记为弃用，输入弃用原因
  - 已弃用工件图标变为灰色 + 红色斜线，一目了然
  - 弃用元数据存储在 `~/.config/gradle-buddy/cache/deprecated-artifacts.json`，跨项目共享
- **弃用工件 Inspection**：`.gradle.kts` 文件中引用已弃用工件的 `libs.xxx.yyy` 表达式显示删除线警告
  - 高亮类型为 `LIKE_DEPRECATED`（删除线 + 弱警告）
  - 悬停显示弃用原因
- **自定义 Gradle 风格图标**：绿色渐变包裹图标（正常）/ 灰色 + 红线（弃用），支持暗色主题

### 🔧 改进
- **plugin.xml**：新增 `<depends>org.toml.lang</depends>`，确保 TOML PSI 在运行时可用
- **inline table 过滤**：gutter 图标只显示在顶层 library 条目上，不会在 `{ group = "...", name = "..." }` 内部重复显示

### 🏗️ 新模块
- **gradle-buddy-linemarker**：独立的行标记模块
  - `VersionCatalogLineMarkerProvider`：TOML gutter 图标
  - `DeprecateArtifactAction`：弃用/取消弃用操作
  - `DeprecatedArtifactService`：application-level 弃用缓存服务
  - `DeprecatedArtifactInspection`：`.gradle.kts` 弃用警告 inspection

## [2026.01.26] - 2026-01-26

### ✨ 新增功能
- **Select other versions（KTS/TOML）**：在 `.gradle.kts` 和 `libs.versions.toml` 中选择任意版本并替换
  - KTS 支持硬编码依赖和 `libs.xxx.yyy` 引用
  - TOML 支持 `module` / `group+name` / `version.ref` / 直接 `version` 格式
- **Catalog -> Hardcoded**：新增意图将 `implementation(libs.xxx.yyy)` 转为硬编码依赖字符串

### 🔧 改进
- **版本目录解析**：支持扫描多模块下的 `gradle/*.versions.toml`，并优先使用设置中的路径
- **TOML 解析**：依赖行支持尾部注释

---

## [2026.01.25] - 2026-01-25

### ✨ 新增功能
- **版本选择对话框**：统一的版本选择 UI，用于 “Select other versions”

---

## [2026.01.24] - 2026-01-24

### 🐛 修复
- **KTS 引用格式**：将硬编码依赖转换为 TOML 后，KTS 引用使用点号访问（如 `libs.hutool.all`）

---

## [Unreleased] - 2025-01-23

### ✨ 新增功能
- **版本目录引用修复器**：智能检测和修复无效的版本目录引用
  - **智能相似度匹配**：使用多因子评分算法（完全匹配 50%、Jaccard 相似度 30%、顺序相似度 20%）查找所有匹配的候选项
  - **无限制候选项**：显示所有至少有一个 token 匹配的别名，按相似度排序
  - **浏览替代项**：新增意图操作，即使引用有效也可以浏览其他候选项
  - **光标位置无关**：无论光标在表达式的哪个位置，都能提取完整的 token 列表
  - **上下文菜单 UI**：智能弹出菜单显示候选项，包含匹配百分比和匹配的关键词
  - **当前引用标识**：在替代项列表中用 "✓ 当前" 标记当前使用的引用
  - **多模块支持**：递归扫描项目中所有模块的 TOML 文件

### 🔧 改进
- **所有意图操作**：添加 `(Gradle Buddy)` 前缀和英文描述，便于识别插件来源
  - `(Gradle Buddy) Select correct catalog reference (N candidates)` - 选择正确的版本目录引用
  - `(Gradle Buddy) Browse catalog alternatives (N candidates)` - 浏览其他版本目录引用
  - `(Gradle Buddy) Fix build-logic qualified name` - 修复 build-logic 限定名
  - `(Gradle Buddy) Convert plugin to version catalog format (TOML)` - 将插件转换为版本目录格式
  - `(Gradle Buddy) Convert dependency to version catalog format (TOML)` - 将依赖转换为版本目录格式
  - `(Gradle Buddy) Update dependency to latest version` - 更新依赖到最新版本

### 🐛 修复
- **Token 提取 Bug**：修复光标位置影响 token 提取的问题
  - 现在无论光标在哪个位置，都能找到最顶层的 `KtDotQualifiedExpression`
  - 确保提取所有 token 用于相似度匹配（例如：`libs.bcprov.jdk15to18` → `[bcprov, jdk15to18]`）
- **资源文件位置**：将意图操作描述文件从子模块移动到主模块资源目录
  - 修复 IDE 中意图操作不显示的问题
  - 所有 `intentionDescriptions` 现在位于 `plugins/gradle-buddy/src/main/resources/`

### 📝 文档
- 为新的版本目录引用修复器功能添加了完整文档
  - `SIMILARITY_MATCHING_EXAMPLES.md`：算法概述和评分示例
  - `BUG_FIX_SUMMARY.md`：详细的 bug 修复说明
  - `RESOURCE_FIX.md`：资源文件位置修复指南
  - `INTENTION_TEXT_UPDATE.md`：意图操作文本更新总结
  - `CHANGELOG_LATEST.md`：最新改动总结

### 🎯 使用场景
1. **修复无效引用**：当 `libs.com.google.devtools.ksp.gradle.plugin` 在 TOML 中不存在时
   - 显示所有包含 `com`、`google`、`devtools`、`ksp`、`gradle`、`plugin` 的候选项
   - 示例：`gradle.plugin.ksp`（85% 匹配：gradle, plugin, ksp）

2. **浏览替代项**：即使 `libs.gradle.plugin.ksp` 是有效引用
   - 在引用的任意位置按 `Alt+Enter`
   - 查看 TOML 中所有相关的依赖项
   - 当前引用标记为 "✓ 当前"
   - 轻松切换到其他版本或变体

---

## [Unreleased] - 2025-01-22

### ✨ Added
- **Plugin ID 修复工具**：自动修复 build-logic 预编译脚本插件的 ID 引用
  - 新增 `FixPluginIdIntention`：Alt+Enter 快速修复单个插件 ID
  - 新增 `FixAllPluginIdsAction`：批量修复项目中所有插件 ID
  - 新增 `PluginIdScanner`：递归扫描 build-logic 目录，提取插件元数据
  - 新增 `IdReplacementEngine`：查找和替换插件 ID 引用
  - 支持自动提取 Kotlin 文件的包名
  - 支持嵌套的 build-logic 目录结构
  - 线程安全的 PSI 访问（所有操作都包装在 ReadAction 中）
  - 进度指示器和详细的操作结果通知
- **id-fixer 模块**：独立的插件 ID 修复模块
  - `PluginIdInfo`：插件元数据数据类
  - `ReplacementCandidate`：替换候选位置
  - `ReplacementResult`：替换操作结果
  - 完整的文档和使用示例

### 🐛 Fixed
- 修复 `PluginIdScanner.extractPackageName()` 的线程安全问题
  - 将 PSI 访问移到 ReadAction.compute 块内
  - 避免在后台线程中直接访问 PSI 元素

### 📝 Documentation
- 新增 `id-fixer/README.md`：详细的功能说明和架构文档
- 新增 `id-fixer/CHANGELOG.md`：模块变更记录
- 更新主 README 添加 Plugin ID 修复工具说明
- 新增使用示例和问题背景说明

---

## [Unreleased] - 2025-12-07

### ✨ Added
- **递归依赖推导**：自动分析并加载模块的所有依赖模块
  - 支持 `project(":path:to:module")` 格式
  - 支持 `projects.path.to.module` Type-safe accessors 格式
  - 使用 BFS 算法避免循环依赖
  - 自动忽略注释掉的依赖声明
- **依赖配置支持**：支持所有 Gradle 依赖配置类型
  - `implementation`, `api`, `compileOnly`, `runtimeOnly`
  - `testImplementation`, `testCompileOnly`, `testRuntimeOnly`
  - `annotationProcessor`, `kapt`, `ksp`
- **Gradle 插件版本更新**：在 settings.gradle.kts 中支持插件版本更新
  - 支持 `id("plugin.id") version "version"` 格式
  - 自动从 Gradle Plugin Portal 查询最新版本
  - 与依赖版本更新使用相同的 Alt+Enter 意图操作
- **字符串工具类**：新增 `StringUtils` 替代 dataframe 依赖
  - `toCamelCaseByDelimiters()`: 转换为 camelCase
  - `toKebabCase()`: 转换为 kebab-case
- **JVM 库工具类**：新增 `GradlePluginSearchUtil` 到 addzero-lib-jvm
  - 查询 Gradle 插件最新版本
  - 从 Gradle Plugin Portal API 获取数据
- **测试覆盖**：新增 `OnDemandModuleLoaderTest` 单元测试
- **文档完善**：新增 `RECURSIVE_DEPENDENCY_DETECTION.md` 详细说明递归依赖推导原理

### 🔧 Changed
- `detectModulesFromOpenFiles()` 现在返回包含递归依赖的完整模块集合
- `OnDemandModuleLoader` 新增私有方法：
  - `expandWithDependencies()`: 递归展开模块及其依赖
  - `extractProjectDependencies()`: 从 build.gradle.kts 提取依赖
  - `findBuildFile()`: 查找模块的构建文件
  - `parseProjectDependencies()`: 解析依赖声明（支持两种格式）

### 🐛 Fixed
- 修复按需加载时可能遗漏传递依赖导致编译失败的问题
- 修复注释掉的依赖被错误解析的问题
- 修复缺失 `org.jetbrains.kotlinx.dataframe` 依赖导致的二进制不兼容问题
  - 移除了对 dataframe 库的依赖
  - 使用自实现的字符串工具函数替代
- 修复 `URL(String)` 已弃用 API 使用警告
  - 使用 `URI.toURL()` 替代废弃的构造函数
  - 兼容未来版本的 Java API 变更

### 📝 Documentation
- 更新 README 添加递归依赖推导功能说明
- 新增依赖格式示例和使用场景说明

---

## [2025.11.31] - 2025-11-30

### 🎯 解决的痛点
- **Gradle Sync 慢**：大型多模块项目 Sync 需要 5-10 分钟，现在只需 30 秒
- **内存占用高**：100 个模块全加载占用 8GB+，现在只加载用到的模块
- **手动管理麻烦**：不再需要手动注释 settings.gradle.kts

### ✨ Added
- **StatusBarWidgetFactory**：使用官方稳定 API 注册状态栏组件，替代已废弃的 `addWidget()` 方法
- **模块排除统计**：通知消息显示 `Loaded: X, Excluded: Y, Total: Z`
- **构建模块自动排除**：自动排除 `build-logic`、`buildSrc`、`buildLogic` 等构建基础设施模块
- **settings.gradle.kts 注释增强**：生成的配置块包含统计信息和被排除模块列表

### 🔧 Changed
- **GradleBuddyService 实现 Disposable**：替代实验性的 `ProjectCloseListener`，使用稳定的生命周期管理
- **partitionModules() 函数**：分离有效模块和被排除模块，便于统计和展示

### 🗑️ Removed
- **GradleBuddyProjectManagerListener**：不再需要，由 Disposable 模式替代
- **实验性 API 依赖**：移除 `ProjectCloseListener` 的使用

### 🐛 Fixed
- 修复 `StatusBar.addWidget(StatusBarWidget)` 已弃用警告
- 修复 `ProjectCloseListener` 实验性 API 警告

---

## [2025.11.32] - 2025-11-30

### ✨ Added
- **按需模块加载**：只加载当前打开的编辑器标签页对应的模块

### ✨ 意图操作 (Alt+Enter)
- **Update dependency to latest version**：在依赖声明上按 `Alt+Enter`，自动从 Maven Central 获取最新版本

### 🔄 迁移工具
- **Version Catalog 迁移**：扫描所有 `.gradle.kts` 文件，将硬编码依赖迁移到 `gradle/libs.versions.toml`
- **项目依赖迁移**：将 `project(":module")` 依赖迁移到 Maven 坐标

### 📝 Documentation
- 完整的 README 文档，包含痛点说明和使用方法
- 详细的功能介绍：工具窗口、意图操作、迁移工具
- 代码示例：Version Catalog 迁移前后对比

# gradle-buddy-linemarker

版本目录工件行标记 (Line Marker) 模块，为 `libs.versions.toml` 中的每个 library 条目提供 gutter 图标，并支持跨项目的工件弃用管理。

## 功能

### 🏷️ TOML Gutter 图标

在 `*.versions.toml` 文件的 `[libraries]` 区块中，每个工件旁边显示一个 Gradle 风格的绿色图标。

- 正常工件：绿色包裹图标
- 已弃用工件：灰色包裹 + 红色斜线

右键点击图标可展开操作菜单：

- **标记为弃用**：输入弃用原因，将工件标记为 deprecated
- **取消弃用**：移除弃用标记

### ⚠️ .gradle.kts 弃用警告

在 `.gradle.kts` 文件中，所有引用了已弃用工件的 `libs.xxx.yyy` 表达式会显示删除线警告（`LIKE_DEPRECATED` 高亮），并附带弃用原因。

### 💾 跨项目缓存

弃用元数据存储在 `~/.config/gradle-buddy/cache/deprecated-artifacts.json`，跨项目共享。在 A 项目标记弃用后，B 项目也能看到。

## 架构

```
gradle-buddy-linemarker/
├── src/main/kotlin/.../linemarker/
│   ├── VersionCatalogLineMarkerProvider.kt  # TOML gutter 图标
│   ├── DeprecateArtifactAction.kt           # 弃用/取消弃用操作
│   ├── DeprecatedArtifactService.kt         # 弃用缓存服务 (application-level)
│   └── DeprecatedArtifactInspection.kt      # .gradle.kts 弃用警告 inspection
└── src/main/resources/
    ├── icons/
    │   ├── catalogArtifact.svg              # 正常图标 (Gradle 绿)
    │   ├── catalogArtifact_dark.svg         # 暗色主题
    │   ├── catalogArtifactDeprecated.svg    # 弃用图标 (灰色+红线)
    │   └── catalogArtifactDeprecated_dark.svg
    └── inspectionDescriptions/
        └── DeprecatedCatalogArtifact.html
```

## 注册

在 `plugin.xml` 中注册了以下扩展点：

- `codeInsight.lineMarkerProvider` (language=TOML) → `VersionCatalogLineMarkerProvider`
- `applicationService` → `DeprecatedArtifactService`
- `localInspection` (language=kotlin, shortName=DeprecatedCatalogArtifact) → `DeprecatedArtifactInspection`

## 依赖

- `gradle-buddy-core`：`GradleBuddySettingsService`（获取 TOML 路径配置）
- `org.toml.lang`：TOML PSI 类
- `org.jetbrains.kotlin`：Kotlin PSI 类（inspection 需要）

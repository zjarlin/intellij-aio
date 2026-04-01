# ide-kit packages Kotlin cleanup, source-only search, and project file hiding tools for JetBrains IDEs.

`ide-kit` 是一个面向 JetBrains IDE 的轻量效率插件，当前聚焦三类高频场景：

- Kotlin 属性的冗余显式类型清理
- Find in Files 里的“源码目录”搜索范围
- Project 视图与 VCS 提交列表里的文件隐藏能力

## 功能一览

- `Alt+Enter` 意图动作：对 Kotlin 属性提供 `移除冗余显式类型`
- Kotlin Inspection：自动标出可以安全删掉的冗余显式类型声明
- 搜索范围：在 `Find in Files` 中增加“源码目录” scope，排除常见生成目录
- Project 视图隐藏：右键隐藏选中的文件或目录
- 隐藏项显示切换：在 Project 视图工具栏切换是否显示已隐藏文件
- VCS 提交列表联动：隐藏项会同步从提交变更列表中排除

## 适合谁用

- Kotlin 项目里经常需要清理样板类型声明的开发者
- 想把 `build/`、`target/`、`.gradle/`、`generated/` 等目录排除出全文搜索的人
- 想临时把噪音文件从 Project 视图和变更提交面板里收起来的人

## 安装后怎么用

### 1. 清理 Kotlin 冗余显式类型

当 Kotlin 已经能安全推断属性类型时，`ide-kit` 会提供 inspection 和 `Alt+Enter` 修复。

使用方式：

1. 打开 Kotlin 文件，把光标放到属性声明上
2. 如果该类型可安全移除，IDE 会显示弱提示
3. 按 `Alt+Enter`
4. 选择 `移除冗余显式类型`

示例：

```kotlin
val userName: String = "zjarlin"
val retryCount: Int = 3
```

执行后：

```kotlin
val userName = "zjarlin"
val retryCount = 3
```

这个能力只会在类型推断结果与显式类型语义一致时触发，不会为了“看起来更短”而冒险改写不稳定声明。

如果你想整仓处理，而不是一个个按 `Alt+Enter`：

1. 打开 `Code -> Inspect Code...`
2. 作用域选择整个项目、模块，或自定义目录
3. 运行后筛选 `SmartRedundantExplicitType`
4. 直接执行批量修复

这个流程适合做一次性清理。因为修复结果最终会落到普通代码变更里，所以你可以直接通过 IDE 的提交列表检查改动，不需要插件额外做逐条确认弹窗。

### 2. 在 Find in Files 里只搜源码

`ide-kit` 不再偷偷改写默认搜索范围，而是显式提供一个可选的“源码目录” scope。

使用方式：

1. 打开 `Edit -> Find -> Find in Files`
2. 在范围选择器里切到 `源码目录`
3. 输入搜索词并执行搜索

这个 scope 会优先保留 source root 下的内容，并排除常见生成输出目录，例如：

- `build`
- `out`
- `target`
- `.gradle`
- `generated`

如果你想搜整个项目，继续用默认 Project scope 即可；如果你只想避开生成文件，就选“源码目录”。

### 3. 在 Project 视图里隐藏文件或目录

`ide-kit` 提供的是“项目内隐藏”，不是删除、移动或写 `.gitignore`。

隐藏方式：

1. 在 `Project` 视图中选中一个或多个文件/目录
2. 右键
3. 选择 `隐藏 / Hide from Project`

恢复显示方式：

1. 在 `Project` 工具栏打开 `显示隐藏文件 / Show Hidden Files`
2. 找到之前隐藏的文件或目录
3. 右键选择 `取消隐藏 / Unhide from Project`

补充说明：

- 隐藏项会同步从 VCS 提交列表里排除
- 隐藏状态按项目保存，在当前项目的 workspace state 中持久化
- 打开“显示隐藏文件”只是在当前项目里临时显示，不会丢失隐藏标记

## 示意截图

下面这张图展示了三个主要入口：Kotlin 的 `Alt+Enter` 清理、`Find in Files` 的“源码目录”范围，以及 Project 视图里的隐藏文件操作。

![ide-kit screenshot](docs/ide-kit-usage-overview.png)

## 当前限制

- Kotlin 清理能力当前面向属性显式类型声明，不是对所有 Kotlin 类型标注做批量重写
- “源码目录”是显式可选 scope，不会强制覆盖 IDE 默认搜索行为
- 隐藏文件能力作用于当前项目视图与变更列表，不会修改磁盘文件，也不会改 Git 跟踪状态
- 如果团队共享同一个仓库但各自 IDE 视图偏好不同，隐藏状态不会替代团队级规则文件

## 开发（本仓库）

- 插件根模块：`plugins/ide-kit`
- 插件描述：`plugins/ide-kit/src/main/resources/META-INF/plugin.xml`
- Kotlin 清理实现：`plugins/ide-kit/smart-intentions-kotlin-redundant-explicit-type`
- 搜索 scope 实现：`plugins/ide-kit/smart-intentions-find-source-only`
- 隐藏文件实现：`plugins/ide-kit/smart-intentions-hidden-files`

构建插件：

```bash
./gradlew :plugins:ide-kit:buildPlugin
```

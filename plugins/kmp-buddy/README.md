KMP Buddy for JetBrains IDEs combines Compose refactoring, Compose Blocks, and canvas-based layout authoring in one plugin.

## What This Plugin Does

`kmp-aio` is a Kotlin and Compose productivity plugin focused on three workflows:

- source-first Compose refactoring through intentions and inspections
- Compose Blocks editing for structure-aware Compose code browsing
- canvas-based low-code layout drafting that writes real Compose code

It also includes Koin cleanup tools for Kotlin projects using dependency injection.

## 中文说明

`kmp-aio` 是一个面向 JetBrains IDE 的 Kotlin / Compose 效率插件，当前主要覆盖三类能力：

- Compose 源码重构意图与静态检查
- Compose Blocks 积木视图与结构化源码浏览
- 画布式低代码布局草图与 Compose 代码生成

另外还包含面向 Koin 的依赖清理能力。

## Main Feature Areas

### 1. Compose Intentions And Refactors

The plugin adds many `Alt+Enter` intentions for Compose functions and call sites:

| Intention | What It Does | Typical Use |
| --- | --- | --- |
| `ExpandWrapperSignatureIntention` | Expands wrapper parameters into a more explicit Compose signature. | When a wrapper composable hides too much state or event structure. |
| `GenerateWrapperPropsIntention` | Generates wrapper props structures from current parameters. | When you want to move toward a props-style API. |
| `NormalizeComposeSignatureIntention` | Reorders or normalizes Compose parameters into a cleaner contract. | When function signatures drift over time. |
| `FlattenUsedObjectParametersIntention` | Flattens object parameters that are only partially used. | When a composable depends on an oversized model object. |
| `ModifierChainIntention` | Extracts or normalizes modifier chains. | When modifiers become noisy, duplicated, or hard to reuse. |
| `UiStateIntention` | Extracts UI state into an explicit state model. | When state is spread across many primitive parameters. |
| `EventsIntention` | Extracts event callbacks into a grouped event contract. | When event lambdas become too numerous. |
| `StateHoistIntention` | Hoists internal state to the caller. | When a composable should become more reusable and controlled. |
| `UiVariantIntention` | Splits visual variants into clearer variant APIs. | When one composable handles too many UI branches. |
| `SlotExtractIntention` | Extracts child regions into slot lambdas. | When internal content should become customizable. |
| `EffectKeysIntention` | Extracts or normalizes effect keys. | When `LaunchedEffect` or related effects use unstable keys. |
| `SectionSplitIntention` | Splits large composables into sections. | When a file contains a very long function body. |
| `StateMapperIntention` | Generates state mapping layers. | When domain state and UI state should be decoupled. |
| `PreviewSampleIntention` | Generates preview sample data or wrappers. | When previews are missing or repetitive to write by hand. |
| `ViewModelInlineIntention` | Reworks ViewModel usage near the UI boundary. | When ViewModel plumbing is too entangled in UI code. |
| `CallArgFillIntention` | Fills missing call arguments. | When adapting callers after a signature change. |
| `CallArgExtractSingleIntention` | Extracts one call argument into a named construct. | When a single inline argument becomes complex. |
| `CallArgExtractBatchIntention` | Extracts multiple call arguments in one pass. | When many inline arguments need cleanup together. |
| `ContainerWrapIntention` | Wraps selected UI with a layout container. | When introducing `Box`, `Row`, `Column`, or another wrapper. |

These are designed to turn large or messy composables into clearer contracts with more explicit state, event, slot, and preview structure.

### 1. Compose 意图与重构

插件为 Compose 函数和调用点提供了一组 `Alt+Enter` 意图：

| 意图 | 作用 | 适用场景 |
| --- | --- | --- |
| `ExpandWrapperSignatureIntention` | 将 wrapper 的隐式参数展开为更明确的 Compose 签名。 | 适合 wrapper 把状态、事件藏得太深的情况。 |
| `GenerateWrapperPropsIntention` | 基于当前参数生成 props 风格结构。 | 适合准备把接口收敛成 props 模型时使用。 |
| `NormalizeComposeSignatureIntention` | 统一、整理 Compose 函数参数顺序与结构。 | 适合签名长期演进后变乱的函数。 |
| `FlattenUsedObjectParametersIntention` | 将实际只用一部分字段的大对象参数拍平。 | 适合 composable 对外部 model 耦合过重的情况。 |
| `ModifierChainIntention` | 提取或规范 modifier 链。 | 适合 modifier 过长、重复、难复用时。 |
| `UiStateIntention` | 提取 UI State。 | 适合状态参数过多、过散时。 |
| `EventsIntention` | 提取事件集合。 | 适合回调 lambda 太多时。 |
| `StateHoistIntention` | 将内部状态提升给调用方。 | 适合把组件改为可控组件时。 |
| `UiVariantIntention` | 拆分视觉变体。 | 适合一个组件内部塞了过多 UI 分支。 |
| `SlotExtractIntention` | 提取 slot lambda。 | 适合内部子区域需要开放定制时。 |
| `EffectKeysIntention` | 提取或规范 effect keys。 | 适合 `LaunchedEffect` 等 key 不稳定时。 |
| `SectionSplitIntention` | 将大函数切成多个 section。 | 适合超长 composable。 |
| `StateMapperIntention` | 生成状态映射层。 | 适合业务状态与 UI 状态解耦。 |
| `PreviewSampleIntention` | 生成预览样例数据或包装。 | 适合预览样板代码重复较多时。 |
| `ViewModelInlineIntention` | 调整 ViewModel 与 UI 边界附近的写法。 | 适合 ViewModel 使用方式过度耦合 UI 时。 |
| `CallArgFillIntention` | 自动补全调用参数。 | 适合函数签名修改后的调用点修复。 |
| `CallArgExtractSingleIntention` | 提取单个调用参数。 | 适合某一个内联参数已经过于复杂时。 |
| `CallArgExtractBatchIntention` | 批量提取多个调用参数。 | 适合一次性清理多个复杂参数。 |
| `ContainerWrapIntention` | 用布局容器包裹当前 UI。 | 适合补 `Box`、`Row`、`Column` 等外层容器。 |

这些意图的核心目标，是把过于庞杂的 Compose 代码收敛成更稳定、可维护、可组合的 API 结构。

### 2. Compose Inspections

The plugin registers Kotlin inspections for common Compose problems:

- wrapper structure inspection
- parameter explosion inspection
- Compose stability inspection
- effect keys inspection

These help detect composables that are becoming too wide, unstable, or unsafe to maintain.

### 2. Compose 静态检查

插件当前包含几类 Compose 检查：

- wrapper 结构检查
- 参数爆炸检查
- Compose 稳定性检查
- effect keys 检查

这些检查主要用于尽早发现函数签名过宽、状态不稳定、effect key 不安全等维护风险。

### 3. Compose Blocks View

Compose Blocks is the structure-aware editor layer for Compose Kotlin files.

It provides:

- a dedicated `Open Compose Blocks` entry from editor and tools menus
- block parsing for Compose files
- block-based browsing of composable structure
- split rendering between block view and source
- source-coupled highlighting and folding
- inline remarks/comments bound to blocks
- block wrap and unwrap operations
- block selection synchronized with the corresponding code region

The goal of Compose Blocks is not to replace code editing. It makes the Compose tree easier to navigate while still keeping the Kotlin source visible and editable.

### 3. Compose Blocks 积木视图

Compose Blocks 是面向 Compose Kotlin 文件的结构化编辑层，不是另起一套“伪编辑器”。

它主要提供：

- `Open Compose Blocks` 打开入口
- Compose 结构解析与积木树浏览
- 积木与源码之间的联动定位
- 源码高亮、折叠、结构浏览
- 与积木绑定的备注 / remark
- wrap / unwrap 等块级操作

目标是让你在“仍然编辑真实 Kotlin 代码”的前提下，更轻松地看清 Compose 的层级结构。

### 4. Canvas Layout Designer

The Compose Designer tool window provides a canvas workflow for sketching layout structure and generating Compose code.

Current capabilities include:

- left palette with built-in components
- support for custom user-defined palette components
- drag-and-drop insertion onto the canvas
- root container based editing
- `Box`, `Row`, and `Column` oriented layout authoring
- sketch-region / draw-to-create layout flow
- named slots and higher-order layout container generation
- mutual exclusion and collision-aware placement
- auto-growth of layout containers to fit children
- toolbar-based editing modes
- `Shift + drag` support for drawing/reparenting interactions
- delete selected element from canvas
- clear canvas support
- arrange / one-click organize to reduce free-positioned output

This workflow is intentionally layout-first. The canvas is meant for building structure and placement, not for fine-grained visual styling.

### 4. Canvas 画布设计器

Compose Designer 工具窗口提供一个偏“布局骨架优先”的画布工作流，用于草图式搭建 Compose 布局并生成代码。

当前能力包括：

- 左侧组件面板
- 内置组件与用户自定义组件
- 拖拽插入画布
- 以根容器为基础的结构编辑
- 以 `Box` / `Row` / `Column` 为核心的布局生成
- 画布框选 / 草图区域生成布局
- 具名插槽与高阶布局容器生成
- 元素互斥、碰撞规避
- 父容器自动撑大以容纳子元素
- 基于工具栏的显式模式切换
- `Shift + Drag` 辅助交互
- 删除当前选中元素
- 清空画布
- `Arrange / 一键整理`，尽量把自由摆放整理成结构化布局

这个设计器当前不是完整视觉设计工具，而是一个“低代码布局骨架编辑器”。

### 5. Generated Compose File Workflow

The designer writes to a generated sibling Kotlin file instead of a detached preview buffer.

That workflow includes:

- creating or opening a generated composable file beside the current file
- keeping the generated file selected while the panel is active
- syncing canvas changes back into the Kotlin file
- generating valid Compose code for built-in palette items
- reducing unnecessary `offset(...)` usage after arrange/normalization
- package inference based on the target directory and sibling Kotlin files

This keeps the output auditable and editable as normal project code.

### 5. 生成代码文件工作流

设计器不会把结果写进临时预览缓存，而是直接在当前目录旁生成或更新 Kotlin 文件。

这部分包括：

- 在当前文件同级目录生成 `GeneratedComposable` 文件
- 面板激活时自动保持对应生成文件被选中
- 画布与 Kotlin 文件双向同步
- 为内置组件生成可编译的 Compose 代码
- 通过整理能力尽量减少无意义的 `offset(...)`
- 基于目标目录与同级 Kotlin 文件推断 package

这样做的核心好处是：输出始终是“真实项目代码”，不是黑盒状态。

### 6. Koin Cleanup Tools

The plugin also includes Koin-specific productivity actions:

- remove redundant constructor dependencies when another injected dependency already exposes them
- project-wide cleanup for `@Single(binds = [...])`

These tools are available from standard intention entry points and are designed for bulk cleanup without hand-editing every declaration.

### 6. Koin 清理工具

插件还包含 Koin 相关的清理能力：

- 当某个已注入依赖已经暴露所需能力时，移除冗余构造参数依赖
- 清理项目中的 `@Single(binds = [...])`

这些能力仍然通过标准意图入口触发，适合做项目级批量治理。

## Built-In UI Elements In The Designer

The current built-in palette includes layout and primitive items such as:

- `Box`
- `Row`
- `Column`
- `Text`
- `Button`
- `Icon`
- `Image`
- `Card`
- `Divider`

The palette can also be extended with your own reusable components through plugin settings and the designer-side add flow.

### 中文补充

当前内置组件主要包括：

- `Box`
- `Row`
- `Column`
- `Text`
- `Button`
- `Icon`
- `Image`
- `Card`
- `Divider`

另外也支持通过设置页与设计器侧入口，添加用户自己的组件定义。

## Actions Added To The IDE

The plugin contributes these main actions:

- `Open Compose Blocks`
- `New Compose Blocks Screen`
- `New Compose Blocks Dialog`
- `New Compose Blocks Page`
- normalize Compose signature from refactoring/editor menus

## Typical Workflows

### Refactor Existing Compose Code

1. Open a Kotlin Compose file.
2. Use `Alt+Enter` on a composable or call site.
3. Apply the desired extraction, normalization, or wrapping intention.

### Inspect And Navigate Large Composables

1. Open a Compose Kotlin file.
2. Run `Open Compose Blocks`.
3. Browse the composable tree while editing the related Kotlin source.

### Draft A Screen From Layout Skeleton

1. Open the Compose Designer tool window.
2. Drag built-in or custom components onto the canvas.
3. Use layout containers to organize the structure.
4. Run `Arrange / 一键整理` to reduce free-positioning.
5. Continue editing the generated Kotlin file directly if needed.

## Module Layout

- `plugins/kmp-buddy`: root IntelliJ plugin descriptor and shared registrations
- `plugins/kmp-buddy/kmp-buddy-designer`: canvas designer and writeback workflow
- `plugins/kmp-buddy/kmp-buddy-blocks`: Compose Blocks editor, parser, builder, and file integration
- `plugins/kmp-buddy/smart-intentions-koin-redundant-dependency`: redundant Koin dependency cleanup
- `plugins/kmp-buddy/smart-intentions-koin-single-binds`: `@Single(binds = ...)` cleanup

## Current Positioning

This plugin is strongest when you want to:

- restructure large Compose codebases faster
- navigate Compose source as blocks instead of raw text only
- draft layout skeletons visually and then continue in Kotlin
- keep generated UI code as real project files instead of hidden metadata

It is not a full visual styling tool. The designer is intentionally biased toward layout structure, container semantics, and source generation.

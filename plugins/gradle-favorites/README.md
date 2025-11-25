# Gradle Favorites Plugin

一个用于管理和快速执行常用 Gradle 任务的 IntelliJ IDEA 插件。

## 功能特性

### 1. 收藏 Gradle 任务
- 在 Gradle 工具窗口中右键点击任务
- 选择 "Add to Favorites" 添加到收藏
- 支持多模块项目,自动记录模块路径
- **NEW:** 可自定义任务分组 (Build, Publish, Test等)

### 2. 快速执行收藏的任务
- **NEW:** 按 `Ctrl+Shift+G` (Windows/Linux) 或 `Cmd+Shift+G` (macOS) 快速弹出收藏列表
- 在编辑器中右键打开上下文菜单
- 找到 "Gradle Favorites" 子菜单
- 自动过滤并显示当前模块的收藏任务
- 点击即可执行

### 3. 任务分组管理
- 将任务组织到自定义分组中
- 工具窗口中按组显示,带组头标识
- 弹出菜单支持分层导航
- 自动按组名和顺序排序

### 4. 搜索和过滤
- 工具窗口顶部实时搜索框
- 按任务名快速过滤
- 搜索结果保持分组显示

### 5. 智能通知提醒
- 打开文件时自动检测当前模块
- 如果该模块有收藏的任务,显示通知气泡
- 可直接在通知中点击执行任务
- 每个模块首次打开时提醒一次

## 使用场景

适用于需要频繁执行特定 Gradle 任务的场景:

- **代码生成**: `kspKotlin`, `generateProto`
- **发布操作**: `publishToMavenCentral`, `publishToMavenLocal`
- **清理构建**: `clean`, `cleanBuildCache`
- **测试运行**: `test`, `integrationTest`

## 使用示例

### 添加收藏
1. 打开 Gradle 工具窗口 (右侧边栏)
2. 找到目标任务,例如 `:lib:tool-psi:kspKotlin`
3. 右键点击任务
4. 选择 "Add to Favorites"

### 执行收藏的任务
**方式一:编辑器菜单**
1. 打开 `lib/tool-psi` 模块下的任意文件
2. 右键打开上下文菜单
3. 选择 "Gradle Favorites" → "kspKotlin"

**方式二:通知气泡**
1. 打开 `lib/tool-psi` 模块下的文件
2. 看到通知气泡提示有 2 个收藏任务
3. 直接点击通知中的任务名称执行

### 移除收藏
1. 在 Gradle 工具窗口找到已收藏的任务
2. 右键点击
3. 选择 "Remove from Favorites"

## 技术架构

### 核心组件
- **GradleFavoritesService**: 持久化存储服务
- **GradleTaskStrategyRegistry**: 策略注册表
- **GradleToolWindowContextStrategy**: Gradle 面板上下文策略
- **EditorContextStrategy**: 编辑器上下文策略

### 数据模型
```kotlin
FavoriteGradleTask(
    projectPath: ":lib:tool-psi",
    taskName: "kspKotlin",
    displayName: ":lib:tool-psi:kspKotlin"
)
```

### 策略模式
使用策略模式处理不同上下文:
- `GradleToolWindowContextStrategy.support()` - 判断是否在 Gradle 面板
- `EditorContextStrategy.support()` - 判断是否在编辑器
- 自动选择合适的策略执行任务

## 依赖要求

- IntelliJ IDEA 2024.2+
- Gradle Plugin
- Java 11+

## 构建

```bash
./gradlew :plugins:gradle-favorites:build
```

## 安装

**方法一: 从源码构建安装**
1. 构建插件: `./gradlew :plugins:gradle-favorites:build`
2. 找到插件包: `plugins/gradle-favorites/build/distributions/gradle-favorites-*.zip`
3. IntelliJ IDEA → Settings → Plugins → ⚙️ → Install Plugin from Disk
4. 选择 zip 文件并重启 IDE

**方法二: 开发调试**
```bash
./gradlew :plugins:gradle-favorites:runIde
```
这会启动一个带有插件的测试 IDE 实例。

## 快速使用

### 1. 打开工具窗口
右侧边栏找到 "Gradle Favorites" (星形图标)

### 2. 添加第一个收藏
- 点击 "Add Favorite"
- 模块路径: `:lib:tool-psi`
- 任务名称: `kspKotlin`
- **NEW:** 选择或输入分组名称: `Build`

### 3. 使用快捷键快速执行
- 按 `Ctrl+Shift+G` (Windows/Linux) 或 `Cmd+Shift+G` (macOS)
- 如果有多个分组,先选择分组
- 然后选择要执行的任务

### 4. 在编辑器中执行
- 打开 `lib/tool-psi/src/` 下的任意文件
- 右键菜单 → "Gradle Favorites" → 选择任务

### 5. 使用搜索功能
- 在工具窗口顶部的搜索框中输入关键词
- 实时过滤任务列表

### 6. 查看通知
打开模块文件时会自动提醒可用的收藏任务。

详细使用说明请查看 [USAGE.md](./USAGE.md)。

# Gradle Favorites Plugin

一个用于管理和快速执行常用 Gradle 任务的 IntelliJ IDEA 插件。

## 功能特性

### 1. 收藏 Gradle 任务
- 在 Gradle 工具窗口中右键点击任务
- 选择 "Add to Favorites" 添加到收藏
- 支持多模块项目,自动记录模块路径

### 2. 快速执行收藏的任务
- 在编辑器中右键打开上下文菜单
- 找到 "Gradle Favorites" 子菜单
- 自动过滤并显示当前模块的收藏任务
- 点击即可执行

### 3. 智能通知提醒
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

构建后在 `plugins/gradle-favorites/build/distributions/` 找到插件 zip 包,通过 IDE 的 "Install Plugin from Disk" 安装。

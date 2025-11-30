# Gradle Buddy

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

## 前提条件 (Prerequisites)

> **重要**：本插件要求项目中的每个模块都是**独立可运行**的。
>
> 这意味着每个模块应该：
> - 有自己完整的 `build.gradle` 或 `build.gradle.kts`
> - 能够独立编译和运行，不强依赖其他模块的编译产物
> - 模块间依赖应通过 Maven 坐标或 `includeBuild` 的方式引入，而非直接 `implementation(project(":other-module"))`
>
> 如果模块之间存在强耦合依赖，使用一键迁移模块依赖到 Maven中央仓库依赖 功能

## 功能特性

- **按需加载**：打开文件时检测所属模块是否已加载，未加载则在状态栏显示提示
- **自动释放**：30秒内没有打开某模块的任何文件，自动 unload 该模块以释放资源
- **智能检测**：自动识别 `build.gradle`、`build.gradle.kts`、`settings.gradle`、`settings.gradle.kts`
- **安全保护**：`includeBuild` 和 `buildSrc` 模块永不释放，确保构建正常

## 使用方法

1. 在 IntelliJ IDEA 中打开 Gradle 项目
2. 打开某个模块的文件时，如果该模块未加载，状态栏显示 "Gradle: Load Required"
3. 点击状态栏指示器触发模块加载

## 安装

```bash
./gradlew :plugins:gradle-buddy:buildPlugin
```

在 `plugins/gradle-buddy/build/libs/` 找到生成的 zip 文件，通过 IntelliJ IDEA 插件管理器安装。

## 工作原理

1. **文件监听**：监控 FileEditorManager 的文件打开/关闭事件
2. **模块追踪**：维护当前打开的模块文件集合
3. **定时检查**：每30秒检查一次，释放没有打开任何文件的模块
4. **加载提示**：检测到未加载的 Gradle 模块时，在状态栏显示加载提示

## 按需加载模块 (On-Demand Module Loading)

核心功能：只加载当前打开的编辑器标签页对应的模块。

### 使用方法

1. 在 IDE 中打开你需要工作的文件（可以打开多个文件）
2. 菜单栏选择 **Tools → Load Only Open Tab Modules**
3. 或使用快捷键 `Ctrl+Alt+Shift+L`
4. 插件会：
    - 获取当前所有打开的编辑器标签页
    - 从文件路径推导对应的 Gradle 模块
    - 修改 `settings.gradle.kts`，只 include 这些模块
    - 自动触发 Gradle 同步

### 恢复所有模块

如果需要恢复所有被排除的模块：

1. 菜单栏选择 **Tools → Restore All Gradle Modules**
2. 插件会取消所有被注释的 include 语句并同步

### 流程图

```
获取打开的标签页
       ↓
推导文件所属模块
       ↓
生成 include 语句
       ↓
更新 settings.gradle.kts
       ↓
触发 Gradle 同步
```

## 配置

当前版本暂无可配置选项，释放超时时间固定为30秒。

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


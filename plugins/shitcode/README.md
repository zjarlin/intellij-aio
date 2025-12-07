# ShitCode Plugin

## 简介

ShitCode 是一个 IntelliJ IDEA 插件，用于标记和管理项目中需要重构的代码。通过使用自定义注解标记"垃圾代码"，帮助开发者追踪和清理技术债务。

## 功能特性

- ✅ 使用 `@Shit` 注解标记需要重构的代码
- ✅ 支持标记类、方法、字段、属性等代码元素
- ✅ 在工具窗口中统一查看所有标记的代码
- ✅ 支持 Java 和 Kotlin 双语言
- ✅ 双击列表项快速跳转到对应代码位置
- ✅ 支持批量删除标记的代码元素
- ✅ 可自定义注解名称

## 安装

1. 从 IntelliJ IDEA 插件市场搜索 "ShitCode" 并安装
2. 或者下载插件 JAR 文件，在 IDEA 中通过 `Settings → Plugins → Install Plugin from Disk` 安装

## 使用方法

### 1. 标记垃圾代码

在需要重构的代码上添加 `@Shit` 注解：

**Kotlin 示例：**
```kotlin
@Shit
class LegacyUserService {
    @Shit
    fun getUserById(id: Long): User {
        // 需要重构的老旧实现
    }
}
```

**Java 示例：**
```java
@Shit
public class LegacyUserService {
    @Shit
    public User getUserById(Long id) {
        // 需要重构的老旧实现
    }
}
```

### 2. 查看垃圾代码列表

1. 点击 IDEA 右侧工具栏的 "ShitCode" 按钮打开工具窗口
2. 工具窗口会按文件分组显示所有标记了 `@Shit` 注解的代码元素
3. 双击列表项可快速跳转到对应代码位置

> 💡 **提示**：如果列表为空，请检查：
> - 是否等待索引构建完成（IDEA 底部状态栏）
> - 注解是否正确拼写（默认为 `@Shit`）
> - 注解是否已导入（需要 import 语句）
> - 点击"刷新"按钮重新扫描

### 3. 管理垃圾代码

工具窗口提供三个操作按钮：

- **刷新**：重新扫描项目，更新垃圾代码列表
- **删除选中**：删除选中的代码元素（支持多选）
- **全部删除**：删除所有标记的代码元素

> ⚠️ 注意：删除操作会永久删除代码，请谨慎使用！

## 配置

在 `Settings → Tools → ShitCode` 中可以自定义注解名称：

- **垃圾代码注解名称**：默认为 "Shit"，可以修改为任意名称（例如：TODO、FIXME、Deprecated 等）

## 最佳实践

1. **代码审查**：在代码审查时使用 `@Shit` 标记需要改进的代码
2. **技术债务追踪**：定期查看 ShitCode 工具窗口，规划重构工作
3. **重构前标记**：在重构大型项目前，先标记所有需要改进的地方
4. **团队约定**：团队可以统一使用自定义注解名称，如 `@NeedRefactor`

## 开发

### 构建插件

```bash
./gradlew :plugins:shitcode:build
```

### 运行测试 IDE

```bash
./gradlew :plugins:shitcode:runIde
```

### 构建发布包

```bash
./gradlew :plugins:shitcode:buildPlugin
```

生成的插件文件位于 `plugins/shitcode/build/distributions/` 目录。

## 技术实现

- 基于 IntelliJ Platform SDK 开发
- 使用 PSI (Program Structure Interface) 扫描 Java/Kotlin 代码
- 使用 Kotlin 编写
- 支持 IntelliJ IDEA K2 模式

## 系统要求

- IntelliJ IDEA 2024.2.5 或更高版本
- JDK 17 或更高版本

## 许可证

本项目使用与主项目相同的许可证。

## 贡献

欢迎提交 Issue 和 Pull Request！

## 联系方式

- 作者：zjarlin
- Email：zjarlin@outlook.com
- 项目主页：https://gitee.com/zjarlin/intellij-aio.git

# Gradle Buddy - Fix Catalog Reference

## 功能概述

这个模块用于检测和修复 Gradle 版本目录（Version Catalog）中的无效依赖引用。

## 问题场景

在使用 Gradle 版本目录时，依赖的引用格式需要遵循特定规则：

### TOML 声明示例

```toml
[libraries]
my-library = { group = "com.example", name = "my-library", version = "1.0.0" }

[plugins]
gradle-plugin-ksp = { id = "com.google.devtools.ksp", version = "1.9.0" }
```

### 正确的引用方式

```kotlin
dependencies {
    // 库依赖：下划线和连字符转换为点
    implementation(libs.my.library)

    // 插件依赖：gradle-plugin-xxx 转换为 gradle.plugin.xxx
    implementation(libs.gradle.plugin.ksp)
}
```

### 常见错误

```kotlin
dependencies {
    // ❌ 错误：使用了驼峰命名
    implementation(libs.gradlePlugin.ksp)

    // ❌ 错误：使用了下划线
    implementation(libs.my_library)
}
```

## 功能特性

1. **自动检测**：扫描所有 `.gradle.kts` 文件，检测无效的版本目录引用
2. **黄色波浪线标识**：在编辑器中用黄色波浪线标识错误的引用
3. **智能修复**：提供快速修复（Quick Fix），自动替换为正确的引用
4. **批量修复**：一次性修复项目中所有相同的错误引用
5. **多目录支持**：支持自定义目录名称（如 `libs`、`zlibs`、`klibs` 等）

## 命名规则

### 库依赖（libraries）

TOML 中的键名转换规则：
- 连字符 `-` → 点 `.`
- 下划线 `_` → 点 `.`

示例：
- `my-library` → `libs.my.library`
- `my_library` → `libs.my.library`
- `spring-boot-starter` → `libs.spring.boot.starter`

### 插件依赖（plugins）

特殊规则：
- `gradle-plugin-xxx` → `libs.gradle.plugin.xxx`
- 其他插件遵循库依赖的规则

示例：
- `gradle-plugin-ksp` → `libs.gradle.plugin.ksp`
- `kotlin-jvm` → `libs.kotlin.jvm`

## 使用方法

1. 在 `.gradle.kts` 文件中编写依赖引用
2. 如果引用无效，会显示黄色波浪线
3. 按 `Alt+Enter`（或 `Option+Enter`）打开快速修复菜单
4. 选择"替换为正确的引用"
5. 插件会自动在整个项目中替换所有相同的错误引用

## 技术实现

### 架构设计

采用**策略模式**来处理不同类型的错误：

#### 1. 错误类型（CatalogReferenceError）

使用 sealed class 定义两种错误类型：

- **WrongFormat**：TOML 中有声明，但引用格式不对
  - 例如：TOML 中声明了 `gradle-plugin-ksp`，代码中写成了 `gradlePlugin.ksp`
  - 修复方式：替换为正确格式 `gradle.plugin.ksp`

- **NotDeclared**：TOML 中根本没有这个声明
  - 例如：代码中使用了 `libs.some.library`，但 TOML 中没有对应声明
  - 修复方式：提供相似声明建议，或提示用户手动添加到 TOML

#### 2. 修复策略（CatalogFixStrategy）

每个策略实现三个方法：

```kotlin
interface CatalogFixStrategy {
    // 判断是否支持修复此类型的错误
    fun support(error: CatalogReferenceError): Boolean

    // 创建快速修复
    fun createFix(project: Project, error: CatalogReferenceError): LocalQuickFix?

    // 获取修复描述
    fun getFixDescription(error: CatalogReferenceError): String
}
```

#### 3. 具体策略实现

- **WrongFormatFixStrategy**：处理格式错误
  - 自动识别驼峰命名、下划线等错误格式
  - 批量替换项目中所有相同的错误引用

- **NotDeclaredFixStrategy**：处理未声明依赖
  - 使用编辑距离算法查找最相似的声明
  - 如果找到相似声明，提供替换建议
  - 如果找不到，提示用户手动添加到 TOML

#### 4. 策略工厂（CatalogFixStrategyFactory）

负责根据错误类型选择合适的策略：

```kotlin
val strategy = CatalogFixStrategyFactory.getStrategy(error)
val fix = strategy?.createFix(project, error)
```

### 核心组件

- **CatalogReferenceScanner**：扫描 TOML 文件，提取所有声明的依赖别名
- **InvalidCatalogReferenceInspection**：检查 Kotlin 代码中的版本目录引用
- **CatalogFixStrategy**：修复策略接口
- **WrongFormatFixStrategy**：格式错误修复策略
- **NotDeclaredFixStrategy**：未声明依赖修复策略
- **CatalogFixStrategyFactory**：策略工厂

## 支持的目录名称

默认支持以下版本目录名称：
- `libs`（默认）
- `zlibs`
- `klibs`
- `testLibs`

可以通过修改 `InvalidCatalogReferenceInspection` 中的 `catalogNames` 集合来添加更多支持。

## 注意事项

1. 确保 TOML 文件位于 `gradle/` 目录下，且文件名以 `.versions.toml` 结尾
2. 插件会自动识别目录名称（从文件名推断，如 `libs.versions.toml` → `libs`）
3. 修复操作会影响整个项目中的所有相同引用，请谨慎使用

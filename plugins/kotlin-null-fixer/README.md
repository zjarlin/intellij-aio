# Kotlin Null Fixer

IntelliJ IDEA 插件，一键修复 Kotlin 空安全错误。

## 功能

将 nullable receiver 上的不安全调用（`.`）自动替换为安全调用（`?.`）。

## 使用方法

### 方式一：悬浮工具条
在 Kotlin 文件编辑器右上角出现的悬浮工具条中点击修复按钮。

### 方式二：右键菜单
在编辑器中右键，选择 **Fix Null Safety → ?.**

### 方式三：Tools 菜单
点击 **Tools → Fix Null Safety → ?.**

## 示例

修复前：
```kotlin
fun process(user: User?) {
    user.name  // 编译错误：Unsafe call on nullable receiver
    user.getAddress().street
}
```

修复后：
```kotlin
fun process(user: User?) {
    user?.name
    user?.getAddress()?.street
}
```

## 支持版本

- IntelliJ IDEA 2024.2+
- Kotlin 插件
- 同时支持 Kotlin 1.x 和 2.x (K2 编译器)

## 安装

在 IntelliJ IDEA 中：
1. 打开 **Settings → Plugins**
2. 点击 **Install plugin from disk**
3. 选择插件 JAR 文件

或从 JetBrains Marketplace 搜索 "Kotlin Null Fixer" 安装。

## 许可

See root `LICENSE`.

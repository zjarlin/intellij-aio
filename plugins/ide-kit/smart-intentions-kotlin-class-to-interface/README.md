# Convert simple Kotlin class/data class declarations into interface contracts through an Alt+Enter intention.

这个模块为 `ide-kit` 提供一条 Kotlin 意图动作：

- 在 `class` 或 `data class` 声明上按 `Alt+Enter`
- 选择 `转换为 interface`
- 把适合抽象成契约的简单类直接改成 `interface`

## 适用场景

适合这类代码：

- 主要承载一组属性定义
- 需要从“具体实现”回收成“接口契约”
- 还没有复杂初始化、副作用或构造逻辑

典型例子：

```kotlin
data class S3Config(
    val endpoint: String,
    val region: String,
    val bucket: String,
    val accessKey: String,
    val secretKey: String,
)
```

执行意图后：

```kotlin
interface S3Config {
    val endpoint: String
    val region: String
    val bucket: String
    val accessKey: String
    val secretKey: String
}
```

## 使用方式

1. 打开 Kotlin 文件
2. 把光标放到 `class` 或 `data class` 声明头部
3. 按 `Alt+Enter`
4. 选择 `转换为 interface`

## 当前支持的转换

当前实现是保守转换，只处理能稳定生成合法接口的情况：

- 主构造参数全部都是 `val` / `var`
- 主构造参数都带显式类型
- 没有 `init` 块
- 没有次构造函数
- 没有父类构造调用，例如 `: Base()`
- 类体里没有普通属性声明

如果类体里已经有函数，函数会原样保留在转换后的接口里。

## 当前不会提供意图的情况

下面这些情况目前会直接跳过，不显示该意图：

- 存在普通构造参数，例如 `class User(name: String)`
- 存在 `init` 初始化逻辑
- 存在次构造函数
- 存在父类构造调用
- 主构造属性参数带注解
- 类体里声明了属性
- `sealed class`、`value class`、`inner class`

这是有意为之。因为这些场景通常不只是“把关键字从 `class` 改成 `interface`”这么简单，继续强行改写很容易生成语义错误或非法 Kotlin 代码。

## 代码位置

- 实现入口：`src/main/kotlin/site/addzero/smart/intentions/kotlin/classtointerface/SmartConvertClassToInterfaceIntention.kt`
- 分析与改写：`src/main/kotlin/site/addzero/smart/intentions/kotlin/classtointerface/ClassToInterfaceSupport.kt`
- 测试：`src/test/kotlin/site/addzero/smart/intentions/kotlin/classtointerface/SmartConvertClassToInterfaceIntentionTest.kt`

## 模块定位

这个模块是 `plugins/ide-kit` 的一个子模块，不是独立发布插件。实际注册由 `ide-kit` 根插件的 `plugin.xml` 完成。

# Remove noisy Kotlinx Json serializer arguments from encode/decode calls.

这个模块为 `ide-kit` 提供一条 Kotlin 意图动作：

- 在 `json.encodeToString(Foo.serializer(), value)` 上按 `Alt+Enter`
- 选择 `移除 Kotlinx Json 显式 serializer() 参数`
- 改写为 `json.encodeToString(value)`

同样支持：

```kotlin
json.decodeFromString(Foo.serializer(), source)
```

改写为：

```kotlin
json.decodeFromString(source)
```

## 设计边界

这是一个保守的代码坏味道清理：只处理第一个参数是 `SomeType.serializer()`、第二个参数是实际值或源码字符串的两参数调用。带命名参数、额外参数、非 `serializer()` 的显式序列化器都会跳过，避免生成语义不确定的代码。

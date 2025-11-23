# Maven Dependency Search - 快速使用指南

## 🚀 快速开始（3步搞定）

### 1️⃣ 打开搜索
按键盘 **`Shift + Shift`** （快速按两次 Shift 键）

### 2️⃣ 切换到 Maven 标签
在弹出的搜索窗口顶部，找到并点击 **`Maven Dependencies`** 标签

### 3️⃣ 输入并搜索
输入你要搜索的内容，例如：
- `guice` - 搜索 Google Guice
- `spring-boot` - 搜索 Spring Boot
- `com.google.inject:guice` - 精确搜索

### 4️⃣ 复制依赖
点击搜索结果，依赖声明会自动复制到剪贴板！

## 📝 搜索技巧

### 关键词搜索
```
搜索: jackson
结果: 所有包含 jackson 的依赖
```

### GroupId 搜索
```
搜索: com.google.inject
结果: Google Inject 组下的所有依赖
```

### 精确坐标搜索
```
搜索: com.google.inject:guice
结果: 只显示 Guice 依赖
```

## ⚙️ 修改输出格式

进入 **`Settings → Tools → Maven Search`**，选择你喜欢的格式：

- **Maven XML** - 适合 Maven 项目
- **Gradle Kotlin DSL** - 适合 Kotlin Gradle 项目（推荐）
- **Gradle Groovy DSL** - 适合传统 Gradle 项目

## 💡 使用场景

### 场景 1: 添加新依赖
```
1. 按 Shift + Shift
2. 切换到 Maven Dependencies
3. 搜索 "lombok"
4. 选择 org.projectlombok:lombok
5. 粘贴到 build.gradle.kts
```

### 场景 2: 更新依赖版本
```
1. 搜索完整坐标: "com.google.inject:guice"
2. 查看最新版本
3. 复制并替换旧版本
```

### 场景 3: 探索相关依赖
```
1. 搜索 "spring-boot-starter"
2. 浏览所有 Spring Boot Starter
3. 找到需要的 starter 并复制
```

## 🎯 常见问题

### Q: 为什么搜索不到结果？
A: 确保输入至少 2 个字符，并检查网络连接

### Q: 如何更改依赖格式？
A: Settings → Tools → Maven Search → Dependency format

### Q: 支持哪些仓库？
A: 目前只支持 Maven Central

### Q: 搜索速度慢怎么办？
A: 可以在设置中调整搜索超时时间

## 🔥 快捷键汇总

| 操作 | 快捷键 |
|------|--------|
| 打开搜索 | `Shift + Shift` |
| 选择结果 | `Enter` 或 鼠标点击 |
| 取消搜索 | `Esc` |

## 📖 输出示例

### Gradle Kotlin DSL
```kotlin
implementation("com.google.inject:guice:7.0.0")
```

### Gradle Groovy DSL
```groovy
implementation 'com.google.inject:guice:7.0.0'
```

### Maven XML
```xml
<dependency>
    <groupId>com.google.inject</groupId>
    <artifactId>guice</artifactId>
    <version>7.0.0</version>
</dependency>
```

---

**开始享受快速搜索的乐趣吧！** ⚡

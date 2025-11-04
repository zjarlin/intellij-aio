# LSI (Language Structure Interface) 语言结构接口

## 概述

LSI 是一个抽象层，旨在统一不同平台（如 Java PSI、Kotlin PSI 等）的解析接口，提供一致的 API 来处理各种编程语言的结构。

## 核心接口

1. **LsiClass** - 类结构抽象
2. **LsiField** - 字段结构抽象
3. **LsiAnnotation** - 注解结构抽象
4. **LsiFile** - 文件结构抽象
5. **LsiProject** - 项目结构抽象

## 工厂模式

通过 `LsiFactory` 创建各种 LSI 对象：

```kotlin
// 获取 PSI 工厂
val factory = LsiManager.getFactory("psi")

// 创建 LsiClass
val lsiClass = factory.createClass(psiClass)
```

## 实现示例

### Java PSI 实现
- PsiLsiClass
- PsiLsiField
- PsiLsiAnnotation

### Kotlin PSI 实现
- KtLsiClass
- KtLsiField
- KtLsiAnnotation

## 使用方法

```kotlin
// 通过 LSI 接口统一处理不同语言的类
fun processClass(lsiClass: LsiClass) {
    println("Class name: ${lsiClass.name}")
    println("Fields count: ${lsiClass.fields.size}")
    
    lsiClass.fields.forEach { field ->
        println("  Field: ${field.name} of type ${field.typeName}")
    }
}
```

## 扩展性

可以通过实现 `LsiFactory` 接口来支持新的语言或平台，并通过 `LsiManager.registerFactory()` 进行注册。
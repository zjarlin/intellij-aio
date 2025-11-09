# LSI (Language Structure Interface) 语言结构接口

## 概述

LSI 是一个抽象层，旨在统一不同平台（如 Java PSI、Kotlin PSI、Java Reflection 等）的解析接口，提供一致的 API 来处理各种编程语言的结构。

## 核心接口

1. **LsiClass** - 类结构抽象
2. **LsiField** - 字段结构抽象
   - `isStatic`: 是否为静态字段
   - `isConstant`: 是否为常量字段
   - `isCollectionType`: 是否为集合类型
   - `isDbField`: 是否为数据库字段（非静态 && 非集合）
3. **LsiType** - 类型结构抽象
4. **LsiAnnotation** - 注解结构抽象
5. **LsiFile** - 文件结构抽象
6. **LsiProject** - 项目结构抽象

## 实现层

### 1. PSI 实现 (Java)
位于 `impl/psi/` 目录：
- `PsiLsiClass` - Java PSI 类适配器
- `PsiLsiField` - Java PSI 字段适配器
- `PsiLsiType` - Java PSI 类型适配器
- `PsiLsiAnnotation` - Java PSI 注解适配器
- `PsiFieldAnalyzers` - PSI 字段分析器集合
- `PsiTypeAnalyzers` - PSI 类型分析器集合

### 2. Kotlin 实现
位于 `impl/kt/` 目录：
- `KtLsiClass` - Kotlin PSI 类适配器
- `KtLsiField` - Kotlin PSI 字段适配器
- `KtLsiType` - Kotlin PSI 类型适配器
- `KtLsiAnnotation` - Kotlin PSI 注解适配器
- `KtFieldAnalyzers` - Kotlin 字段分析器集合

### 3. 反射实现
位于 `impl/clazz/` 目录：
- `ClazzLsiClass` - Java Reflection 类适配器
- `ClazzLsiField` - Java Reflection 字段适配器
- `ClazzLsiType` - Java Reflection 类型适配器
- `ClazzLsiAnnotation` - Java Reflection 注解适配器
- `ClazzFieldAnalyzers` - 反射字段分析器集合

## 分析器模式

为了避免过长的实现方法，LSI 层使用分析器（Analyzer）模式将复杂逻辑提取到独立的工具类中：

### 字段分析器示例

```kotlin
// PSI 字段分析器
object PsiFieldAnalyzers {
    object StaticFieldAnalyzer {
        fun isStaticField(psiField: PsiField): Boolean {
            return psiField.hasModifierProperty(PsiModifier.STATIC)
        }
    }

    object CollectionTypeAnalyzer {
        fun isCollectionType(psiField: PsiField): Boolean {
            // 判断逻辑...
        }
    }
}

// 在 PsiLsiField 中使用
class PsiLsiField(private val psiField: PsiField) : LsiField {
    override val isStatic: Boolean
        get() = PsiFieldAnalyzers.StaticFieldAnalyzer.isStaticField(psiField)
}
```

## 使用方法

### 基本使用

```kotlin
// 通过 LSI 接口统一处理不同语言的类
fun processClass(lsiClass: LsiClass) {
    println("Class name: ${lsiClass.name}")
    println("Fields count: ${lsiClass.fields.size}")

    lsiClass.fields.forEach { field ->
        println("  Field: ${field.name} of type ${field.typeName}")
        println("    Is static: ${field.isStatic}")
        println("    Is collection: ${field.isCollectionType}")
        println("    Is DB field: ${field.isDbField}")
    }
}
```

### 便捷扩展函数

为了方便使用，LSI 提供了扩展函数将 PSI/Kt 对象转换为 LSI 对象：

```kotlin
import site.addzero.util.lsi.toLsiClass
import site.addzero.util.lsi.toLsiField
import site.addzero.util.lsi.isDbField
import site.addzero.util.lsi.getDbFields
import site.addzero.util.lsi.getAllDbFields

// 将 PsiClass 转换为 LsiClass
val psiClass: PsiClass = ...
val lsiClass: LsiClass = psiClass.toLsiClass()

// 将 PsiField 转换为 LsiField
val psiField: PsiField = ...
val lsiField: LsiField = psiField.toLsiField()

// 或者直接使用便捷方法
if (psiField.isDbField()) {
    // 这个方法内部会转换为 LSI 并检查
}

// 获取类的数据库字段
val dbFields: List<LsiField> = psiClass.getDbFields()

// 获取类的所有数据库字段（包括继承的）
val allDbFields: List<LsiField> = psiClass.getAllDbFields()

// Kotlin 也一样
val ktClass: KtClass = ...
val dbFields: List<LsiField> = ktClass.getDbFields()
```

### 数据库字段提取

LSI 提供了便捷的方法来提取数据库相关的字段：

```kotlin
// 方法 1: 使用 LsiClass 的属性
fun getDbFields(lsiClass: LsiClass): List<LsiField> {
    return lsiClass.dbFields  // 自动过滤掉静态字段和集合字段
}

// 方法 2: 使用扩展函数（推荐）
fun getDbFields(psiClass: PsiClass): List<LsiField> {
    return psiClass.getDbFields()
}

// 获取所有数据库字段（包括继承的）
fun getAllDbFields(psiClass: PsiClass): List<LsiField> {
    return psiClass.getAllDbFields()
}

// 访问字段的元信息
psiClass.getDbFields().forEach { field ->
    println("Field: ${field.name}")
    println("  Type: ${field.typeName}")
    println("  Column: ${field.columnName ?: field.name}")
    println("  Comment: ${field.comment}")
}
```

### 使用场景示例

```kotlin
// 过滤非静态字段
fun getNonStaticFields(lsiClass: LsiClass): List<LsiField> {
    return lsiClass.fields.filter { !it.isStatic }
}

// 过滤数据库字段（非静态且非集合）
fun getDbFields(lsiClass: LsiClass): List<LsiField> {
    return lsiClass.fields.filter { it.isDbField }
}

// 检查类是否有特定注解
fun hasJpaEntity(lsiClass: LsiClass): Boolean {
    return lsiClass.hasAnnotation(
        "javax.persistence.Entity",
        "jakarta.persistence.Entity"
    )
}
```

## 设计原则

1. **统一接口**：所有语言/平台实现都遵循相同的 LSI 接口
2. **分析器分离**：复杂的分析逻辑提取到 Analyzer 对象中，保持适配器类简洁
3. **懒加载**：使用 Kotlin 的 `get()` 属性避免不必要的计算
4. **可扩展**：容易添加新的语言支持或新的分析功能

## 扩展性

### 添加新的分析器

当需要添加新的字段分析功能时：

1. 在对应的 `*FieldAnalyzers` 对象中添加新的分析器对象
2. 在 `LsiField` 接口中添加新的属性
3. 在各个实现类中委托给相应的分析器

示例：

```kotlin
// 1. 在分析器中添加
object PsiFieldAnalyzers {
    object TransientFieldAnalyzer {
        fun isTransient(psiField: PsiField): Boolean {
            return psiField.hasAnnotation("javax.persistence.Transient")
        }
    }
}

// 2. 在接口中添加
interface LsiField {
    val isTransient: Boolean
}

// 3. 在实现中使用
class PsiLsiField(private val psiField: PsiField) : LsiField {
    override val isTransient: Boolean
        get() = PsiFieldAnalyzers.TransientFieldAnalyzer.isTransient(psiField)
}
```

## LSI 与 JavaFieldMetaInfo 的关系

### 设计分层

项目中有两个字段元信息的表示：

1. **LsiField** - 语言无关的抽象接口，用于 IDE/PSI 层
2. **JavaFieldMetaInfo** - 具体数据类，用于 DDL 生成层

```kotlin
// LsiField - 抽象接口
interface LsiField {
    val name: String?           // 字段名
    val typeName: String?       // 类型名称（字符串）
    val type: LsiType           // LSI 类型抽象
    val comment: String?        // 字段注释
    val columnName: String?     // 数据库列名
    // ...其他属性
}

// JavaFieldMetaInfo - 具体数据类
data class JavaFieldMetaInfo(
    val name: String,           // 字段名
    val type: Class<*>,         // Java 反射类型
    val genericType: Class<*>,  // 泛型类型
    val comment: String         // 字段注释
)
```

### 使用场景

- **LsiField**：在 PSI 解析和字段元数据提取时使用，提供语言无关的统一接口
- **JavaFieldMetaInfo**：在 DDL 生成时使用，需要 Java 反射进行类型检查（`Class.isAssignableFrom()`）

### 桥接模式

现有的代码已经实现了桥接模式。例如 `PsiUtil.getJavaFieldMetaInfo()` 方法：

```kotlin
fun getJavaFieldMetaInfo(psiClass: PsiClass): List<JavaFieldMetaInfo> {
    val fieldsMetaInfo = mutableListOf<JavaFieldMetaInfo>()

    // 使用 LSI 过滤数据库字段
    val fields = psiClass.allFields.filter { it.isDbField() }.toList()

    for (field in fields) {
        val fieldType = field.type

        // 使用 LSI 获取列名
        val guessColumnName = field.toLsiField().columnName
        val columnName = guessColumnName ?: field.name

        // 使用 LSI 获取注释
        val fieldComment = field.guessFieldComment()

        // 转换为 Java 反射类型
        val typeClass = getJavaClassFromPsiType(fieldType)

        // 创建 JavaFieldMetaInfo
        fieldsMetaInfo.add(
            JavaFieldMetaInfo(
                name = columnName,
                type = typeClass,
                genericType = typeClass,
                comment = fieldComment
            )
        )
    }
    return fieldsMetaInfo
}
```

这种设计的优点：
1. **职责分离**：LSI 负责解析，JavaFieldMetaInfo 负责类型检查
2. **类型安全**：DDL 生成层使用 Java 反射进行精确的类型匹配
3. **可测试性**：可以在不依赖 PSI 的情况下测试 DDL 生成逻辑

## 原则：避免直接使用 PSI/KtClass

在插件代码中，应该始终通过 LSI 层访问类结构，而不是直接使用 `PsiClass`、`KtClass` 或 `PsiField`。这样可以：

1. 保持代码的语言无关性
2. 统一不同平台的 API 差异
3. 简化测试（可以用反射实现进行单元测试）
4. 提高代码可维护性

**不推荐**：
```kotlin
fun PsiField.isStaticField(): Boolean {
    return hasModifierProperty(PsiModifier.STATIC)
}
```

**推荐**：
```kotlin
// 使用 LSI 抽象
fun processField(field: LsiField) {
    if (field.isStatic) {
        // 处理静态字段
    }
}
```

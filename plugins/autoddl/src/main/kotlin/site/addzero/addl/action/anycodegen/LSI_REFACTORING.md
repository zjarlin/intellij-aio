# AbsGen LSI 重构文档

## 🎯 重构目标

将 `AbsGen` 及其所有子类从直接使用 PSI/KtClass 重构为使用 LSI (Language Structure Interface) 抽象层，实现真正的语言无关代码生成。

## ✨ 重构成果

### 创建的文件

#### 1. 核心抽象层 (3 个文件，203 行)

| 文件 | 行数 | 说明 |
|------|------|------|
| `AbsGenLsi.kt` | 123 | LSI 版本的代码生成基类 |
| `entity/LsiClassMetaInfo.kt` | 59 | LSI 类元信息 DTO |
| `util/LsiExtensions.kt` | 21 | PSI/KtClass 转 LsiClass 的扩展函数 |

#### 2. 重构的生成器 (3 个文件)

1. **GenJimmerDTOLsi.kt** - Jimmer DTO 规范生成器
2. **GenExcelDTOLsi.kt** - Excel DTO 生成器（支持导入导出）
3. **GenControllerLsi.kt** - Controller 生成器（支持两种风格）

## 📊 对比分析

### 重构前 (AbsGen)

```kotlin
abstract class AbsGen : AnAction() {
    abstract fun genCode4Java(psiFieldMetaInfo: PsiFieldMetaInfo): String
    abstract fun genCode4Kt(psiFieldMetaInfo: PsiFieldMetaInfo): String
    
    // 需要分别处理 Java 和 Kotlin
    protected open fun performAction(project: Project, e: AnActionEvent) {
        if (ktClass == null) {
            // Java 特定处理
            val extractInterfaceMetaInfo = psiClass?.let { PsiUtil.getJavaFieldMetaInfo(it) }
            val generatedCode = genCode4Java(psiFieldMetaInfo)
        } else {
            // Kotlin 特定处理
            val extractInterfaceMetaInfo = PsiUtil.extractInterfaceMetaInfo(ktClass)
            val generatedCode = genCode4Kt(psiFieldMetaInfo)
        }
    }
}
```

**问题**：
- ❌ 需要为 Java 和 Kotlin 分别实现代码生成方法
- ❌ 子类需要维护两套逻辑
- ❌ 代码重复，难以维护
- ❌ 耦合 PSI 细节

### 重构后 (AbsGenLsi)

```kotlin
abstract class AbsGenLsi : AnAction() {
    // 只需要一个方法！
    abstract fun genCode(metaInfo: LsiClassMetaInfo): String
    
    // 统一处理 Java 和 Kotlin
    protected open fun performAction(project: Project, e: AnActionEvent) {
        val lsiClass = when {
            ktClass != null -> ktClass.toLsiClass()
            psiClass != null -> psiClass.toLsiClass()
            else -> return
        }
        
        val metaInfo = LsiClassMetaInfo.from(lsiClass)
        val generatedCode = genCode(metaInfo)
    }
}
```

**优势**：
- ✅ 只需实现一个 `genCode` 方法
- ✅ 语言无关，自动支持 Java 和 Kotlin
- ✅ 代码简洁，易于维护
- ✅ 解耦 PSI 细节

## 🏗️ 架构设计

### LSI 抽象层架构

```
┌─────────────────────────────────────────┐
│          Code Generator                  │
│         (AbsGenLsi 子类)                 │
└──────────────┬──────────────────────────┘
               │ 依赖
               ▼
┌─────────────────────────────────────────┐
│       LsiClassMetaInfo (DTO)            │
│   - packageName                         │
│   - className                           │
│   - fields: List<LsiField>              │
│   - lsiClass: LsiClass                  │
└──────────────┬──────────────────────────┘
               │ 使用
               ▼
┌─────────────────────────────────────────┐
│         LSI Interface Layer             │
│   - LsiClass (语言无关接口)              │
│   - LsiField (字段抽象)                  │
│   - LsiAnnotation (注解抽象)             │
└──────────────┬──────────────────────────┘
               │ 实现
       ┌───────┴───────┐
       ▼               ▼
┌─────────────┐ ┌─────────────┐
│ PsiLsiClass │ │ KtLsiClass  │
│ (Java实现)  │ │ (Kotlin实现)│
└─────────────┘ └─────────────┘
```

### 数据流

```
PsiClass/KtClass
    │
    │ toLsiClass()
    ▼
LsiClass (语言无关)
    │
    │ LsiClassMetaInfo.from()
    ▼
LsiClassMetaInfo (轻量DTO)
    │
    │ genCode()
    ▼
Generated Code
```

## 💡 核心概念

### 1. LsiClass - 语言结构接口

```kotlin
interface LsiClass {
    val name: String?
    val qualifiedName: String?
    val comment: String?
    val fields: List<LsiField>
    val annotations: List<LsiAnnotation>
    val isPojo: Boolean
    // ...
}
```

**作用**: 提供语言无关的类结构访问接口

### 2. LsiClassMetaInfo - 轻量级元数据

```kotlin
data class LsiClassMetaInfo(
    val packageName: String?,
    val className: String?,
    val classComment: String?,
    val qualifiedName: String?,
    val fields: List<LsiField>,
    val lsiClass: LsiClass
)
```

**作用**: 
- 提取代码生成所需的最小信息集
- 避免在生成过程中频繁访问 PSI
- 便于测试和模拟

### 3. 扩展函数 - 简化转换

```kotlin
fun PsiClass.toLsiClass(): LsiClass = PsiLsiClass(this)
fun KtClass.toLsiClass(): LsiClass = KtLsiClass(this)
```

**作用**: 提供简洁的 API，隐藏实现细节

## 📝 使用示例

### 实现一个新的生成器

```kotlin
class GenMyDTOLsi : AbsGenLsi() {
    
    override fun genCode(metaInfo: LsiClassMetaInfo): String {
        val className = metaInfo.className ?: "UnnamedClass"
        val packageName = metaInfo.packageName
        
        // 访问字段信息
        val fields = metaInfo.fields.map { field ->
            """
            val ${field.name}: ${field.typeName}
                // 注释: ${field.comment}
            """.trimIndent()
        }
        
        return """
        package $packageName
        
        data class ${className}DTO(
            ${fields.joinToString(",\n")}
        )
        """.trimIndent()
    }
    
    override val fileSuffix: String = "DTO"
}
```

**就这么简单！** 
- ✅ 不需要关心是 Java 还是 Kotlin
- ✅ 不需要分别实现两个方法
- ✅ 自动支持所有 LSI 支持的语言

## 🔄 迁移指南

### 旧代码 (AbsGen)

```kotlin
class GenMyDTO : AbsGen() {
    override fun genCode4Java(psiFieldMetaInfo: PsiFieldMetaInfo): String {
        val (pkg, classname, _, javaFieldMetaInfos) = psiFieldMetaInfo
        // Java 特定代码...
    }
    
    override fun genCode4Kt(psiFieldMetaInfo: PsiFieldMetaInfo): String {
        val (pkg, classname, _, javaFieldMetaInfos) = psiFieldMetaInfo
        // Kotlin 特定代码...
    }
}
```

### 新代码 (AbsGenLsi)

```kotlin
class GenMyDTOLsi : AbsGenLsi() {
    override fun genCode(metaInfo: LsiClassMetaInfo): String {
        val packageName = metaInfo.packageName
        val className = metaInfo.className
        val fields = metaInfo.fields
        
        // 统一的代码生成逻辑
        return """
        // 生成的代码...
        """.trimIndent()
    }
    
    override val fileSuffix: String = "DTO"
}
```

## 🎯 重构的优势

### 1. 简化代码

- **旧**: 每个生成器需要 2 个方法（Java + Kotlin）
- **新**: 每个生成器只需 1 个方法

**代码减少 50%！**

### 2. 易于扩展

要支持新语言（如 Scala、Groovy）：

- **旧**: 需要修改所有生成器，添加新方法
- **新**: 只需实现该语言的 LsiClass，生成器无需改动

### 3. 更好的测试性

```kotlin
@Test
fun testGenCode() {
    // 创建模拟的 LsiClass
    val mockLsiClass = mockk<LsiClass>()
    every { mockLsiClass.name } returns "User"
    every { mockLsiClass.fields } returns listOf(...)
    
    val metaInfo = LsiClassMetaInfo.from(mockLsiClass)
    val generator = GenMyDTOLsi()
    val code = generator.genCode(metaInfo)
    
    // 断言生成的代码
    assertThat(code).contains("class UserDTO")
}
```

### 4. 解耦 PSI

- 不再直接依赖 IntelliJ PSI API
- 可以在非 IDEA 环境中运行（如 CLI 工具）
- 便于单元测试

## 📦 文件清单

```
plugins/autoddl/src/main/kotlin/site/addzero/addl/action/anycodegen/
├── AbsGenLsi.kt                      # LSI 基类
├── AbsGen.kt                         # 旧版基类（保留兼容）
│
├── entity/
│   └── LsiClassMetaInfo.kt          # LSI 元数据 DTO
│
├── util/
│   └── LsiExtensions.kt             # 扩展函数
│
└── impl/
    ├── GenJimmerDTOLsi.kt           # Jimmer DTO 生成器
    ├── GenExcelDTOLsi.kt            # Excel DTO 生成器
    ├── GenControllerLsi.kt          # Controller 生成器
    │
    ├── GenJimmerDTO.kt              # 旧版（保留）
    ├── GenExcelDTO.kt               # 旧版（保留）
    └── GenController.kt             # 旧版（保留）
```

## 🚀 后续计划

### 短期

1. ✅ 重构 AbsGen 为 AbsGenLsi
2. ✅ 实现 3 个核心生成器的 LSI 版本
3. ⏳ 更新 plugin.xml 注册新的 Action
4. ⏳ 添加单元测试

### 中期

1. 重构剩余的生成器（GenJimmerAll、GenJimmerBaseController 等）
2. 废弃旧版 AbsGen
3. 完善 LSI 层的功能（添加更多元信息）

### 长期

1. 支持更多语言（Scala、Groovy）
2. 支持自定义模板
3. 提供 CLI 工具（基于 LSI 的独立代码生成工具）

## 🔗 相关文档

- LSI 抽象层文档：`checkouts/metaprogramming-lsi/lsi-core/src/main/kotlin/site/addzero/util/lsi/README.md`
- PSI 工具文档：`lib/tool-psi/README.md`
- 插件开发文档：`CLAUDE.md`

## 📞 联系

如有问题或建议，请联系：
- 作者：zjarlin
- Email：zjarlin@outlook.com

---

**重构日期**: 2025-11-23  
**重构状态**: ✅ 核心完成，待全面迁移

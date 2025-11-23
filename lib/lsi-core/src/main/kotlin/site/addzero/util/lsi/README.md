# LSI (Language Structure Interface) 语言结构接口

## 概述

LSI 是一个抽象层，旨在统一不同平台（如 Java PSI、Kotlin PSI、Java Reflection、 APT/KSP 等）解析接口，提供一致的 API 来处理各种编程语言的结构。

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
- ...

### 2. Kotlin 实现
位于 `impl/kt/` 目录：
- `KtLsiClass` - Kotlin PSI 类适配器
- `KtLsiField` - Kotlin PSI 字段适配器
- `KtLsiType` - Kotlin PSI 类型适配器
- `KtLsiAnnotation` - Kotlin PSI 注解适配器
- ...

### 3. 反射实现
位于 `impl/clazz/` 目录：
- `ClazzLsiClass` - Java Reflection 类适配器
- `ClazzLsiField` - Java Reflection 字段适配器
- `ClazzLsiType` - Java Reflection 类型适配器
- ...

## 分析器模式
为了避免过长的实现方法，LSI 适配层层使用大量扩展函数将复杂逻辑提取到独立的咳特灵文件中：
## 设计原则

. **统一接口**：所有语言/平台实现都遵循相同的 LSI 接口
. **可扩展**：容易添加新的语言支持或新的分析功能

## 扩展性
### 使用场景
- **LsiField**：在 PSI 解析和字段元数据提取时使用，提供语言无关的统一接口

### 桥接模式

这种设计的优点：
. **职责分离**：LSI 负责解析
. **可测试性**：可以在不依赖 PSI 的情况下测试 DDL 生成逻辑

## 原则：避免直接使用 PSI/KtClass
在插件代码中，应该始终通过 LSI 层访问类结构，而不是直接使用 `PsiClass`、`KtClass` 或 `PsiField`。这样可以：

1. 保持代码的语言无关性
2. 统一不同平台的 API 差异

# AutoDDL 插件 for IntelliJ IDEA

**AutoDDL** 插件 为IntelliJ IDEA扩展一些元数据操作 。
用户可选择调用大模型自动生成Table，或手动编写表单生成 DDL。

## 生成菜单

* GenDDL 根据当前类生成ddl语句
* GenJimmerDTO 根据当前类生成生成jimmer各种DTO样板代码
* GenExcelDTO 根据当前类生成生成fastexcel框架的实体

## 功能特性

- **表创建**：基于结构化表单元数据生成 `CREATE TABLE` SQL 语句。
- **大模型支持**：调用大语言模型（LLM）自动生成表单元数据。
- **Java 类型映射**：自动将 Java 类型映射为数据库字段类型。

## 使用说明

1. **插件建表功能入口**：在 **Tools** -> **AutoDDL** 中找到插件。
2. **初始配置**：在 **IDEA 设置** 中配置大模型 API Key
3. **调用大模型生成表单**：
    - 使用大模型生成表单元数据，支持手动编辑DDL表单。生成的sql在项目根目录下,可以二次编辑

## 常用意图(alt+回车)

常见的枚举

1. 比如带有枚举注释的字段上alt+回车 选择GenEnum...即可生成Java/Kotlin枚举 枚举相关在IntelliJ IDEA
   设置/其他/AutoDDL设置可以看到(枚举的生成路径默认./当前目录 可以设置包路径:com.example.xxx)

```kotlin
   /**
 * 1=男
 * 2:女
 * 3-其他
 */
val gender: String?
```

2. 现在你可以在build.gradle.kts中把现有依赖upsert到libs.version.toml ,只需alt+回车Convert to
   version catalog
3. 在实体类上alt+回车基于javadoc注释添加swagger注解,excel 注解,包含自定义设置的注解
   它会沿着swagger > excelproperty > javadoc > ai猜测 这个优先级猜注释
   猜到了之后,可以全局设置配生成的目标注解,已有注解不影响 


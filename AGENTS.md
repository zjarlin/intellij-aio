项目概述
AutoDDL 是一个 IntelliJ IDEA 插件，扩展了 Java/Kotlin 实体类的元数据操作。它从实体类生成 DDL（CREATE TABLE 和 ALTER TABLE 语句），并提供可选的 AI 辅助。该插件还提供了意图操作，用于添加注解（Swagger、Excel 属性、自定义注解）以及从字段注释生成枚举。

构建系统
该项目使用自定义的 Gradle 构建系统：

自定义构建逻辑位于 checkouts/build-logic（通过 git 从远程仓库检出）

通过 io.gitee.zjarlin.auto-modules 插件自动生成模块配置

使用 IntelliJ Platform Gradle Plugin 进行插件开发

注意：由于自定义插件（site.addzero.repo-buddy 和 io.gitee.zjarlin.auto-modules）的要求，构建需要 Java 24+ JVM。如果遇到 JVM 版本错误，这是一个已知限制。

常用构建命令
bash
# 构建插件
./gradlew build

# 在测试 IDE 实例中运行插件
./gradlew runIde

# 构建插件分发包
./gradlew buildPlugin

# 验证插件
./gradlew verifyPlugin
项目架构
模块结构
text
autoddl-idea-plugin/
├── lib/
│   ├── tool-common/    # 语言无关的工具类（LSI 抽象层）
│   └── tool-psi/       # PSI 特定工具类
└── src/main/kotlin/    # 主插件代码
关键架构概念
1. LSI（语言结构接口）抽象层
位于 lib/tool-common/src/main/kotlin/com/addzero/util/lsi/，LSI 层提供了语言无关的抽象，用于处理类结构：

LsiClass：类表示的语言无关接口

LsiType：类型信息的语言无关接口

LsiField：字段信息的语言无关接口

LsiAnnotation：注解的语言无关接口

LSI 层有三种实现：

psi：基于 PSI 的 Java 类实现（使用 IntelliJ 的 PSI API）

kt：Kotlin 特定的 KtClass 实现

clazz：基于反射的实现，使用 java.lang.Class

这种抽象允许插件在整个代码库中统一处理 Java 和 Kotlin 类。在处理实体类时，始终使用 LSI 接口而不是直接处理 PsiClass 或 KtClass。

2. DDL 生成流水线
DDL 生成遵循以下流程：

从实体类提取元数据（通过 LSI 层）

创建 DDLContext 对象（包含表名、字段、数据库类型）

选择合适的数据库生成器（MysqlDDLGenerator、PostgreSQLDDLGenerator、OracleDDLGenerator、H2SQLDDLGenerator、DMSQLDDLGenerator 或 TaosSQLDDLGenerator）

生成 SQL 语句（CREATE TABLE 或 ALTER TABLE ADD COLUMN）

在编辑器中打开生成的 SQL（保存到项目根目录的 .autoddl 文件夹）

入口点：

GenDDL 操作：从实体类生成 DDL 的主要操作

DDLContextFactory4JavaMetaInfo：从 Java/Kotlin 类创建 DDLContext 的工厂

IDatabaseGenerator：数据库特定 SQL 生成的接口

3. 意图操作系统
意图操作（Alt+Enter）在启动时通过 AddActionAndIntentions 动态注册：

Swagger 注解：AddSwaggerAnnotationAction / AddSwaggerAnnotationJavaAction

Excel 注解：AddExcelPropertyAnnotationAction / AddExcelPropertyAnnotationJavaAction

自定义注解：AddCusTomAnnotationAction / AddCusTomAnnotationJavaAction

枚举生成：从带有枚举符号的字段注释生成 Java/Kotlin 枚举（例如：1=男 2:女 3-其他）

所有注解意图操作都继承 AbstractDocCommentAnnotationAction，它提供了一个优先级系统来推断缺失的文档：

现有的 Swagger 注解

现有的 Excel 属性注解

Javadoc 注释

基于 AI 的猜测（作为最后手段）

4. 代码生成系统
位于 src/main/kotlin/com/addzero/addl/action/anycodegen/，提供以下生成器：

Jimmer DTOs：生成 Jimmer 框架 DTO 类

Excel DTOs：生成 FastExcel 框架实体

Controllers：生成控制器代码

Jimmer All：生成完整的基于 Jimmer 的 CRUD 脚手架

所有生成器都继承 AbsGen 基类。

5. 设置和配置
设置服务：MyPluginSettingsService（应用级服务）

可配置 UI：MyPluginConfigurable

设置包括：数据库类型、AI 的 API 密钥、枚举生成路径、注解目标

重要文件位置
插件描述符：src/main/resources/META-INF/plugin.xml

LSI 接口：lib/tool-common/src/main/kotlin/com/addzero/util/lsi/

PSI 工具类：lib/tool-common/src/main/kotlin/com/addzero/util/psi/

DDL 生成器：src/main/kotlin/com/addzero/addl/autoddlstarter/generator/

操作类：src/main/kotlin/com/addzero/addl/action/

意图操作：src/main/kotlin/com/addzero/addl/intention/

开发指南
处理实体类
当添加处理实体类的功能时：

使用 LSI 抽象层（LsiClass、LsiType、LsiField）而不是直接访问 PSI 或 KtClass

检查 PsiValidateUtil.isValidTarget() 来验证类是否是有效的实体（POJO 或 Jimmer 实体）

使用 psiCtx() 工具获取当前编辑器上下文

添加新的数据库支持
要添加对新数据库的支持：

在 src/main/kotlin/com/addzero/addl/autoddlstarter/generator/ex/ 中创建一个继承 IDatabaseGenerator 的新类

在 getColumnType() 方法中实现类型映射

在 IDatabaseGenerator.Companion.getDatabaseDDLGenerator() 中注册生成器

更新设置 UI 以包含新的数据库类型

添加新的意图操作
创建继承 AbstractDocCommentAnnotationAction（用于注解操作）或 IntentionAction 的操作类

在 AddActionAndIntentions.kt 启动活动中注册

在 src/main/resources/intentionDescriptions/ 中添加描述资源

工具窗口
该插件提供两个工具窗口：

AutoDDL：用于 DDL 操作的主要工具窗口（右侧边栏）

ShitCode：跟踪标记有 @Shit 注解的代码以进行清理

AI 集成
该插件集成了 LLM API（默认：阿里的 qwen2.5-coder-1.5b-instruct）用于：

从自然语言生成表元数据

当其他方法失败时猜测字段注释

AI 配置在插件设置中（Tools → AutoDDL Settings）。

测试
进行更改时：

构建插件：./gradlew build

在测试 IDE 中运行：./gradlew runIde

使用 Java 和 Kotlin 实体类进行测试

验证目标数据库类型的生成 SQL 语法

重要说明
生成的 SQL 文件保存到 .autoddl/ 文件夹（隐藏文件夹）- 确保这在 .gitignore 中

该插件支持 Jimmer 框架实体（检查 @Entity 和其他 Jimmer 注解）

从注释生成枚举使用模式：1=Male、2:Female、3-Other（分隔符：=、:、-）

构建系统使用配置缓存以实现更快的构建


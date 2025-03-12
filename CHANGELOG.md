#### Version last
- 支持含有枚举的注释生成相关java/kotlin代码
- 现在你可以在build.gradle.kts中把现有依赖upsert到libs.version.toml ,只需alt+回车 
- 在实体类上alt+回车基于javadoc注释添加swagger注解,excel 注解,包含自定义设置的注解
  它会沿着swagger > excelproperty > javadoc > ai猜测 这个优先级猜注释
  猜到了之后,可以全局设置配生成的目标注解,已有注解不影响
- 屎山代码面板(可以在项目中定义@Shit注解,插件会扫描被注解标记的💩屎山代码)

#### Version 1.6
- 新增结构化输出能力，呼出generate上下文，将自然语言转为当前类对应的Json格式。

#### Version 1.5
- 上下文菜单生成ADD COLUMN语句，支持Jimmer框架实体。

#### Version 1.4
- 新功能：自动输出实体对应的增加列，允许用户快速添加字段。在实体生成上下文菜单中使用`GenDDL`。（设置中请配置数据库类型，建表时可动态选择，增加列时采用设置中的数据库类型。）
- 生成的语句可在SQL编辑器中预览，默认保存在项目根路径的`.autoddl`文件夹中（以点开头的隐藏文件夹）。请确保将此文件夹加入`.gitignore`。
- IntelliJ IDEA设置中，新增了多个阿里巴巴模型供选择。
- 默认采用免费的模型`qwen2.5-coder-1.5b-instruct`。

#### Version 1.3
- 在查询LLM时增加了加载效果，按钮禁用，提升用户体验。
- 仅当有数据时才显示删除按钮，Shift键可连续选择，Ctrl键可跳跃选择。
- 添加了表单校验、表名校验和字段区域校验。

#### Version 1.2
- 修复了部分数据库语法错误。
- 添加了LLM能力，支持在Tools菜单下生成CREATE TABLE语句。
- 若LLM填充表单失败，则返回默认表单。

#### Version 1.0
- 首次发布AutoDDL插件。
- 支持在Tools菜单下生成CREATE TABLE语句。
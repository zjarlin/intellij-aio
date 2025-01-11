### English:

#### Version last
- The database plugin supports right-clicking on a schema to incrementally generate and synchronize DDL based on all entities in the project.

#### Version 1.6
- Added structured output capability, which calls out the generate context and converts natural language into the corresponding JSON format for the current class.

#### Version 1.5
- The context menu now supports generating ADD COLUMN statements for Jimmer framework entities.

#### Version 1.4
- New feature: Automatically outputs columns to be added for the entity, allowing users to quickly add fields. In the entity's generate context menu, use `GenAddColumnByThis`. (Configure the database type in settings; dynamic selection during table creation, and the set database type will be used for column additions.)
- Generated statements can be previewed in the SQL editor and are saved by default in the `.autoddl` folder in the project root (hidden folder starting with a dot). Make sure to include this folder in `.gitignore`.
- Multiple Alibaba models have been added for selection in IntelliJ IDEA settings.
- The default model is the free model `qwen2.5-coder-1.5b-instruct`.

#### Version 1.3
- Added loading effect when querying LLM, with buttons disabled during this time for a smoother experience.
- The delete button only shows when there is data. Shift for continuous selection and Ctrl for jumping selection.
- Added form validation, table name validation, and field area validation.

#### Version 1.2
- Fixed some database syntax errors.
- Added LLM capability for generating CREATE TABLE statements in the Tools menu.
- If LLM fails to fill the form, the default form is returned.

#### Version 1.0
- First release of the AutoDDL plugin.
- Supports generating CREATE TABLE statements in the Tools menu.

### 中文：

#### Version last
- database插件支持右键schema，依据项目所有实体差量生成同步DDL。

#### Version 1.6
- 新增结构化输出能力，呼出generate上下文，将自然语言转为当前类对应的Json格式。

#### Version 1.5
- 上下文菜单生成ADD COLUMN语句，支持Jimmer框架实体。

#### Version 1.4
- 新功能：自动输出实体对应的增加列，允许用户快速添加字段。在实体生成上下文菜单中使用`GenAddColumnByThis`。（设置中请配置数据库类型，建表时可动态选择，增加列时采用设置中的数据库类型。）
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
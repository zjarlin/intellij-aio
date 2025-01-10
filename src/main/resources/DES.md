### 中文：

- DataAgentWithStructuredOutput。
- 在实体类alt+insert 或者 ctrl+enter 之后，可以依据实体选择生成代码
- 在IntelliJ IDEA中，可以便捷生成CREATE TABLE语句。用户可以选择调用AI能力，初步生成结构化建表表单（以下简称建表的表单元数据J）。
- 对于表单的元数据J，用户仍可以二次编辑。
- ----
- 用户只需关心Java中的类型，插件会自动映射为数据库CREATE TABLE语句。
- 用户也可以不调用LLM能力，直接手动写表单来生成DDL语句。
- ----
- 插件入口位于Tools菜单 -> AutoDDL。
- 注：用户需要在IDEA设置中配置大语言模型KEY。
- # 目前只支持阿里灵积 DASHSCOPE_API_KEY='sk-xxxxxxxxxx'。
- ---[模型申请地址](https://dashscope.console.aliyun.com/model)---
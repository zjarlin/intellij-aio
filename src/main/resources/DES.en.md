### English:

- DataAgentWithStructuredOutput.
- After the entity class alt+insert or ctrl+enter, the code can be generated 
  according to the entity selection. 
- In IntelliJ IDEA, CREATE TABLE statements can be conveniently generated. Users can choose to use AI to generate an initial structured form for creating tables (referred to as form cell metadata J for creating tables).
- For the form metadata J, users can still make secondary edits.
- ----
- Users only need to worry about Java types, and the plugin will automatically map them into database CREATE TABLE statements for you.
- Users can also manually write forms to generate DDL statements without invoking LLM capabilities.
- ----
- The plugin entry is located under the Tools menu -> AutoDDL.
- Note: Users need to configure the large model KEY in IDEA settings.
- # Currently, only Alibaba DASHSCOPE_API_KEY='sk-xxxxxxxxxx' is supported.
- ---[Model application address](https://dashscope.console.aliyun.com/model)---
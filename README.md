
# AutoDDL Plugin for IntelliJ IDEA

The **AutoDDL** plugin helps you effortlessly generate `CREATE TABLE` statements within IntelliJ IDEA. Users can either rely on the power of large language models (LLMs) to initially generate structured form metadata (hereinafter referred to as **Form Cell Metadata J**) or manually write forms to generate DDL statements.

## Features
- **Effortless Table Creation**: Automatically generate `CREATE TABLE` SQL statements based on structured form metadata.
- **LLM Support**: Users can optionally use large language models to automatically generate the structured form for creating tables.
- **Java Type Mapping**: No need to worry about database column types. The plugin maps Java types to database types and generates the corresponding DDL statements.
- **Manual Editing**: Users can still edit the generated form metadata **J** for further customization of the DDL.
- **Manual Form Writing**: For users who prefer manual control, it's also possible to write forms directly and generate DDL without invoking the LLM capabilities.

## Usage
1. **Plugin Entry**: Find the plugin under the **Tools** menu -> **AutoDDL** in IntelliJ IDEA.
2. **Initial Setup**: Before using the plugin, you need to configure the API Key for the LLM:
    - Currently, only **Alibaba Lingji** (`DASHSCOPE_API_KEY`) is supported.
    - You can configure your API Key in **IDEA Settings**.
3. **Invoking LLM for Table Creation**:
    - Choose to leverage the LLM to generate structured form metadata for table creation.
    - After generation, you can still make manual edits to the form if necessary.
4. **Manual Form Writing**:
    - If preferred, users can manually write the forms to generate DDL statements without relying on the LLM.

### 中文版 `README.zh.md`

```markdown
# AutoDDL 插件 for IntelliJ IDEA
- **idea其他设置中即可配置插件**

**AutoDDL** 插件帮助您在 IntelliJ IDEA 中甜甜的生成 `CREATE TABLE` 语句。用户可以选择调用大模型的能力，自动生成结构化建表表单元数据（以下简称 **建表的表单元数据 J**），也可以手动编写表单来生成 DDL 语句。

![](https://tva1.sinaimg.cn/large/007S8ZIlly1gh7tnluukbj31hc0u0k6u.jpg)
## 功能特性
- **idea其他设置中即可配置插件**
- **自动扫描项目中的实体,与idea 内置的database右键schema即可根据实体类生成ddl语句**
- **内置的database右键schema即可根据数据字典表生成枚举类**
- **表创建**：基于结构化表单元数据自动生成 `CREATE TABLE` SQL 语句。
- **大模型支持**：用户可选择调用大语言模型（LLM）的能力，自动生成建表表单元数据。
- **Java 类型映射**：用户无需关心数据库字段类型，插件会自动将 Java 类型映射到数据库类型，并生成相应的 DDL 语句。
- **二次编辑**：用户仍然可以对生成的表单元数据 **J** 进行二次编辑，以进一步自定义 DDL。
- **手动编写表单**：如果用户不想调用大模型能力，也可以手动编写表单并生成 DDL 语句。

## 使用说明
1. **插件入口**：在 IntelliJ IDEA 的 **Tools** 菜单 -> **AutoDDL** 下找到插件入口。
2. **初始配置**：在使用插件之前，您需要配置 LLM 的 API Key：
- 目前只支持 **阿里灵积**（`DASHSCOPE_API_KEY`）。
- 您可以在 **IDEA 设置** 中配置 API Key。
3. **调用大模型生成建表表单**：
- 选择使用大模型生成建表的结构化表单元数据。
- 生成后，您仍然可以对表单进行手动编辑。
4. **手动编写表单**：

```
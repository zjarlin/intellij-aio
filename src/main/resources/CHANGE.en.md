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
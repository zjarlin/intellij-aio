<#-- Oracle ALTER TABLE: ${className} -->
<#list fields as field>
ALTER TABLE "${tableName?upper_case}" ADD "${field.columnName?upper_case}" ${field.toColumnType(dialect)}<#if !field.nullable> NOT NULL</#if>;
<#if field.comment??>
COMMENT ON COLUMN "${tableName?upper_case}"."${field.columnName?upper_case}" IS '${field.comment}';
</#if>
</#list>

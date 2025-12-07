<#-- H2 DDL: ${className} -->
<#if comment??>
-- ${comment}
</#if>

CREATE TABLE IF NOT EXISTS "${tableName}" (
<#list fields as field>
    "${field.columnName}" ${field.toColumnType(dialect)}<#if field.isPrimaryKey> PRIMARY KEY AUTO_INCREMENT</#if><#if !field.nullable> NOT NULL</#if><#if field_has_next>,</#if>
</#list>
);

<#if comment??>
COMMENT ON TABLE "${tableName}" IS '${comment}';
</#if>
<#list fields as field>
<#if field.comment??>
COMMENT ON COLUMN "${tableName}"."${field.columnName}" IS '${field.comment}';
</#if>
</#list>

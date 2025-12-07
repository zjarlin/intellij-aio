<#-- Oracle DDL: ${className} -->
<#if comment??>
-- ${comment}
</#if>

CREATE TABLE "${tableName?upper_case}" (
<#list fields as field>
    "${field.columnName?upper_case}" ${field.toColumnType(dialect)}<#if !field.nullable> NOT NULL</#if><#if field_has_next>,</#if>
</#list>
<#assign pkFields = fields?filter(f -> f.isPrimaryKey)>
<#if pkFields?size gt 0>
    , CONSTRAINT "PK_${tableName?upper_case}" PRIMARY KEY ("${pkFields?first.columnName?upper_case}")
</#if>
);

<#if comment??>
COMMENT ON TABLE "${tableName?upper_case}" IS '${comment}';
</#if>
<#list fields as field>
<#if field.comment??>
COMMENT ON COLUMN "${tableName?upper_case}"."${field.columnName?upper_case}" IS '${field.comment}';
</#if>
</#list>

<#if pkFields?size gt 0>
-- Sequence for auto-increment
CREATE SEQUENCE "SEQ_${tableName?upper_case}" START WITH 1 INCREMENT BY 1;
</#if>

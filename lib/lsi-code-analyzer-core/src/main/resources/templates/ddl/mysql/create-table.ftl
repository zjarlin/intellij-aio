<#-- MySQL DDL: ${className} -->
<#if comment??>
-- ${comment}
</#if>

CREATE TABLE IF NOT EXISTS `${tableName}` (
<#list fields as field>
    `${field.columnName}` ${field.toColumnType(dialect)}<#if !field.nullable> NOT NULL</#if><#if field.isPrimaryKey> AUTO_INCREMENT</#if><#if field.comment??> COMMENT '${field.comment}'</#if><#if field_has_next>,</#if>
</#list>
<#assign pkFields = fields?filter(f -> f.isPrimaryKey)>
<#if pkFields?size gt 0>
    , PRIMARY KEY (`${pkFields?first.columnName}`)
</#if>
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci<#if comment??> COMMENT='${comment}'</#if>;

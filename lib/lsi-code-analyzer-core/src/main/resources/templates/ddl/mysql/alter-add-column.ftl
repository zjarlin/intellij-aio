<#-- MySQL ALTER TABLE: ${className} -->
<#list fields as field>
ALTER TABLE `${tableName}` ADD COLUMN `${field.columnName}` ${field.toColumnType(dialect)}<#if !field.nullable> NOT NULL</#if><#if field.comment??> COMMENT '${field.comment}'</#if>;
</#list>

<#-- TDengine/TaosDB DDL: ${className} -->
<#if comment??>
-- ${comment}
</#if>

-- 创建超级表 (STable)
CREATE STABLE IF NOT EXISTS ${tableName} (
    ts TIMESTAMP<#list fields as field><#if !field.isPrimaryKey>,
    ${field.columnName} ${field.toColumnType(dialect)}</#if></#list>
) TAGS (
    -- 定义标签列，根据业务需求修改
    tag_id INT
);

-- 创建子表示例
-- CREATE TABLE ${tableName}_001 USING ${tableName} TAGS (1);

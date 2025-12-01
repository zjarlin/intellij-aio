@import site.addzero.lsi.analyzer.ddl.DdlContext
@import site.addzero.lsi.analyzer.ddl.toColumnName
@import site.addzero.lsi.analyzer.ddl.toColumnType
@import site.addzero.lsi.analyzer.ddl.DatabaseDialect

@param ctx: DdlContext

-- TDengine/TaosDB DDL: ${ctx.className}
@if(ctx.comment != null)
-- ${ctx.comment}
@endif

-- 创建超级表 (STable)
CREATE STABLE IF NOT EXISTS ${ctx.tableName} (
    ts TIMESTAMP@for(field in ctx.fields)@if(!field.isPrimaryKey),
    ${field.toColumnName()} ${field.toColumnType(DatabaseDialect.TAOS)}@endif@endfor
) TAGS (
    -- 定义标签列，根据业务需求修改
    tag_id INT
);

-- 创建子表示例
-- CREATE TABLE ${ctx.tableName}_001 USING ${ctx.tableName} TAGS (1);

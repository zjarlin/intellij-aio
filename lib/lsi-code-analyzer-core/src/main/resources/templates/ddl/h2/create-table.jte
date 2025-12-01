@import site.addzero.lsi.analyzer.ddl.DdlContext
@import site.addzero.lsi.analyzer.ddl.toColumnName
@import site.addzero.lsi.analyzer.ddl.toColumnType
@import site.addzero.lsi.analyzer.ddl.DatabaseDialect

@param ctx: DdlContext

-- H2 DDL: ${ctx.className}
@if(ctx.comment != null)
-- ${ctx.comment}
@endif

CREATE TABLE IF NOT EXISTS "${ctx.tableName}" (
@for(field in ctx.fields)
    "${field.toColumnName()}" ${field.toColumnType(DatabaseDialect.H2)}@if(field.isPrimaryKey) PRIMARY KEY AUTO_INCREMENT@endif@if(!field.nullable) NOT NULL@endif@if(!field.equals(ctx.fields.last())),@endif
@endfor
);

@if(ctx.comment != null)
COMMENT ON TABLE "${ctx.tableName}" IS '${ctx.comment}';
@endif
@for(field in ctx.fields)
@if(field.comment != null)
COMMENT ON COLUMN "${ctx.tableName}"."${field.toColumnName()}" IS '${field.comment}';
@endif
@endfor

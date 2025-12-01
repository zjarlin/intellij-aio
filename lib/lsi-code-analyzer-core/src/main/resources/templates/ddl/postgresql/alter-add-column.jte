@import site.addzero.lsi.analyzer.ddl.DdlContext
@import site.addzero.lsi.analyzer.ddl.toColumnName
@import site.addzero.lsi.analyzer.ddl.toColumnType
@import site.addzero.lsi.analyzer.ddl.DatabaseDialect

@param ctx: DdlContext

-- PostgreSQL ALTER TABLE: ${ctx.className}
@for(field in ctx.fields)
ALTER TABLE "${ctx.tableName}" ADD COLUMN "${field.toColumnName()}" ${field.toColumnType(DatabaseDialect.POSTGRESQL)}@if(!field.nullable) NOT NULL@endif;
@if(field.comment != null)
COMMENT ON COLUMN "${ctx.tableName}"."${field.toColumnName()}" IS '${field.comment}';
@endif
@endfor

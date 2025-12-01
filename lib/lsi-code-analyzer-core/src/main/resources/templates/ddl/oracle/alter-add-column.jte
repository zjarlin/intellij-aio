@import site.addzero.lsi.analyzer.ddl.DdlContext
@import site.addzero.lsi.analyzer.ddl.toColumnName
@import site.addzero.lsi.analyzer.ddl.toColumnType
@import site.addzero.lsi.analyzer.ddl.DatabaseDialect

@param ctx: DdlContext

-- Oracle ALTER TABLE: ${ctx.className}
@for(field in ctx.fields)
ALTER TABLE "${ctx.tableName.uppercase()}" ADD "${field.toColumnName().uppercase()}" ${field.toColumnType(DatabaseDialect.ORACLE)}@if(!field.nullable) NOT NULL@endif;
@if(field.comment != null)
COMMENT ON COLUMN "${ctx.tableName.uppercase()}"."${field.toColumnName().uppercase()}" IS '${field.comment}';
@endif
@endfor

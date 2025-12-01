@import site.addzero.lsi.analyzer.ddl.DdlContext
@import site.addzero.lsi.analyzer.ddl.toColumnName
@import site.addzero.lsi.analyzer.ddl.toColumnType
@import site.addzero.lsi.analyzer.ddl.DatabaseDialect

@param ctx: DdlContext

-- MySQL ALTER TABLE: ${ctx.className}
@for(field in ctx.fields)
ALTER TABLE `${ctx.tableName}` ADD COLUMN `${field.toColumnName()}` ${field.toColumnType(DatabaseDialect.MYSQL)}@if(!field.nullable) NOT NULL@endif@if(field.comment != null) COMMENT '${field.comment}'@endif;
@endfor

@import site.addzero.lsi.analyzer.ddl.DdlContext
@import site.addzero.lsi.analyzer.ddl.toColumnName
@import site.addzero.lsi.analyzer.ddl.toColumnType
@import site.addzero.lsi.analyzer.ddl.DatabaseDialect

@param ctx: DdlContext

-- MySQL DDL: ${ctx.className}
@if(ctx.comment != null)
-- ${ctx.comment}
@endif

CREATE TABLE IF NOT EXISTS `${ctx.tableName}` (
@for(field in ctx.fields)
    `${field.toColumnName()}` ${field.toColumnType(DatabaseDialect.MYSQL)}@if(!field.nullable) NOT NULL@endif@if(field.isPrimaryKey) AUTO_INCREMENT@endif@if(field.comment != null) COMMENT '${field.comment}'@endif@if(!field.equals(ctx.fields.last())),@endif
@endfor
@if(ctx.fields.any { it.isPrimaryKey })
    , PRIMARY KEY (`${ctx.fields.first { it.isPrimaryKey }.toColumnName()}`)
@endif
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci@if(ctx.comment != null) COMMENT='${ctx.comment}'@endif;

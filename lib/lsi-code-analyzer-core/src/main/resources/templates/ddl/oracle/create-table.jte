@import site.addzero.lsi.analyzer.ddl.DdlContext
@import site.addzero.lsi.analyzer.ddl.toColumnName
@import site.addzero.lsi.analyzer.ddl.toColumnType
@import site.addzero.lsi.analyzer.ddl.DatabaseDialect

@param ctx: DdlContext

-- Oracle DDL: ${ctx.className}
@if(ctx.comment != null)
-- ${ctx.comment}
@endif

CREATE TABLE "${ctx.tableName.uppercase()}" (
@for(field in ctx.fields)
    "${field.toColumnName().uppercase()}" ${field.toColumnType(DatabaseDialect.ORACLE)}@if(!field.nullable) NOT NULL@endif@if(!field.equals(ctx.fields.last())),@endif
@endfor
@if(ctx.fields.any { it.isPrimaryKey })
    , CONSTRAINT "PK_${ctx.tableName.uppercase()}" PRIMARY KEY ("${ctx.fields.first { it.isPrimaryKey }.toColumnName().uppercase()}")
@endif
);

@if(ctx.comment != null)
COMMENT ON TABLE "${ctx.tableName.uppercase()}" IS '${ctx.comment}';
@endif
@for(field in ctx.fields)
@if(field.comment != null)
COMMENT ON COLUMN "${ctx.tableName.uppercase()}"."${field.toColumnName().uppercase()}" IS '${field.comment}';
@endif
@endfor

@if(ctx.fields.any { it.isPrimaryKey })
-- Sequence for auto-increment
CREATE SEQUENCE "SEQ_${ctx.tableName.uppercase()}" START WITH 1 INCREMENT BY 1;
@endif

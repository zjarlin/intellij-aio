package site.addzero.ddl.parser

import site.addzero.ddl.core.model.ColumnDefinition
import site.addzero.ddl.core.model.TableDefinition
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.field.LsiField

/**
 * 基于LSI的DDL解析器
 *
 * 将LSI抽象的类信息解析为表定义
 */
class LsiDDLParser(
    private val fieldTypeMapper: FieldTypeMapper = FieldTypeMapper(),
    private val annotationExtractor: AnnotationExtractor = AnnotationExtractor()
) {

    /**
     * 解析LSI类为表定义
     *
     * @param lsiClass LSI类
     * @return 表定义
     */
    fun parse(lsiClass: LsiClass): TableDefinition {
        // 直接使用 AnnotationExtractor.extractTableInfo 来解析整个表
        // 它会自动处理字段过滤、列定义等所有逻辑
        return annotationExtractor.extractTableInfo(lsiClass)
    }
}


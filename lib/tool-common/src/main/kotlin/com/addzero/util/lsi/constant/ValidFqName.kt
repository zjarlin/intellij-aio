package com.addzero.util.lsi.constant

internal val ENTITY_ANNOTATIONS = setOf(
    "javax.persistence.Entity",
    "jakarta.persistence.Entity",
    "org.babyfish.jimmer.sql.Entity",
    "org.babyfish.jimmer.sql.MappedSuperclass"
)

internal val TABLE_ANNOTATIONS = setOf(
    "org.babyfish.jimmer.sql.Table"
)

internal val LOMBOK_ANNOTATIONS = setOf(
    "lombok.Data",
    "lombok.Getter",
    "lombok.Setter"
)

internal const val API_MODEL_PROPERTY = "io.swagger.annotations.ApiModelProperty"
internal const val SCHEMA = "io.swagger.v3.oas.annotations.media.Schema"

internal const val COM_BAOMIDOU_MYBATISPLUS_ANNOTATION_TABLE_FIELD = "com.baomidou.mybatisplus.annotation.TableField"
internal const val ENTITY = "javax.persistence.Entity"
internal const val MAPPED_SUPERCLASS = "javax.persistence.MappedSuperclass"


// 集合类型的全限定名列表
internal val COLLECTION_TYPE_FQ_NAMES = setOf(
    "java.util.Collection", "java.util.List", "java.util.Set", "java.util.Map",
    "kotlin.collections.Collection", "kotlin.collections.List", "kotlin.collections.Set",
    "kotlin.collections.Map", "kotlin.collections.ArrayList", "kotlin.collections.LinkedList",
    "kotlin.collections.HashSet", "kotlin.collections.LinkedHashSet",
    "kotlin.collections.HashMap", "kotlin.collections.LinkedHashMap"
)


// 定义注解与对应方法名的映射关系
internal val COMMENT_ANNOTATION_METHOD_MAP = mapOf(
    API_MODEL_PROPERTY to "value",
    SCHEMA to "description",
    "com.alibaba.excel.annotation.ExcelProperty" to "value",
    "cn.idev.excel.annotation.ExcelProperty" to "value",
    "cn.afterturn.easypoi.excel.annotation.Excel" to "name"
)

internal val COLUMN_NAME_ANNOTATION_METHOD_MAP = mapOf(
    "org.babyfish.jimmer.sql.Column" to "name",
    COM_BAOMIDOU_MYBATISPLUS_ANNOTATION_TABLE_FIELD to "value"
)

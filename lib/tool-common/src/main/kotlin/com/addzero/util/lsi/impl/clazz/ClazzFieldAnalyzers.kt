package com.addzero.util.lsi.impl.clazz

import java.lang.reflect.Field
import java.util.*

/**
 * Java Field 分析器集合
 */
object ClazzFieldAnalyzers {

    /**
     * 集合类型分析器
     */
    object CollectionTypeAnalyzer {
        private val COLLECTION_TYPES = setOf(
            Collection::class.java,
            List::class.java,
            Set::class.java,
            Map::class.java,
            ArrayList::class.java,
            LinkedList::class.java,
            HashSet::class.java,
            LinkedHashSet::class.java,
            HashMap::class.java,
            LinkedHashMap::class.java
        )

        fun isCollectionType(field: Field): Boolean {
            return COLLECTION_TYPES.any { it.isAssignableFrom(field.type) }
        }
    }
    
    /**
     * 静态字段分析器
     */
    object StaticFieldAnalyzer {
        fun isStaticField(field: Field): Boolean {
            return java.lang.reflect.Modifier.isStatic(field.modifiers)
        }
    }
    
    /**
     * 常量字段分析器
     */
    object ConstantFieldAnalyzer {
        fun isConstantField(field: Field): Boolean {
            return java.lang.reflect.Modifier.isFinal(field.modifiers) &&
                   java.lang.reflect.Modifier.isStatic(field.modifiers)
        }
    }

    /**
     * 列名分析器（反射版本）
     */
    object ColumnNameAnalyzer {
        fun getColumnName(field: Field): String? {
            // 尝试获取注解
            field.annotations.forEach { annotation ->
                val annotationClass = annotation.annotationClass.java
                when (annotationClass.name) {
                    "org.babyfish.jimmer.sql.Column" -> {
                        try {
                            val nameMethod = annotationClass.getDeclaredMethod("name")
                            val name = nameMethod.invoke(annotation) as? String
                            if (!name.isNullOrBlank()) {
                                return name
                            }
                        } catch (e: Exception) {
                            // 忽略异常
                        }
                    }
                    "com.baomidou.mybatisplus.annotation.TableField" -> {
                        try {
                            val valueMethod = annotationClass.getDeclaredMethod("value")
                            val value = valueMethod.invoke(annotation) as? String
                            if (!value.isNullOrBlank()) {
                                return value
                            }
                        } catch (e: Exception) {
                            // 忽略异常
                        }
                    }
                }
            }
            return null
        }
    }

    /**
     * 注释分析器（反射版本）- 从注解中提取字段描述
     * 注意：反射无法获取文档注释，只能从注解中提取
     */
    object CommentAnalyzer {
        fun getComment(field: Field): String? {
            // 尝试从注解中获取描述
            field.annotations.forEach { annotation ->
                val annotationClass = annotation.annotationClass.java
                val description = when (annotationClass.name) {
                    "io.swagger.annotations.ApiModelProperty" -> {
                        try {
                            val valueMethod = annotationClass.getDeclaredMethod("value")
                            valueMethod.invoke(annotation) as? String
                        } catch (e: Exception) {
                            null
                        }
                    }
                    "io.swagger.v3.oas.annotations.media.Schema" -> {
                        try {
                            val descMethod = annotationClass.getDeclaredMethod("description")
                            descMethod.invoke(annotation) as? String
                        } catch (e: Exception) {
                            null
                        }
                    }
                    "com.alibaba.excel.annotation.ExcelProperty",
                    "cn.idev.excel.annotation.ExcelProperty" -> {
                        try {
                            val valueMethod = annotationClass.getDeclaredMethod("value")
                            valueMethod.invoke(annotation) as? String
                        } catch (e: Exception) {
                            null
                        }
                    }
                    "cn.afterturn.easypoi.excel.annotation.Excel" -> {
                        try {
                            val nameMethod = annotationClass.getDeclaredMethod("name")
                            nameMethod.invoke(annotation) as? String
                        } catch (e: Exception) {
                            null
                        }
                    }
                    else -> null
                }

                if (!description.isNullOrBlank()) {
                    return description
                }
            }
            return null
        }
    }
}

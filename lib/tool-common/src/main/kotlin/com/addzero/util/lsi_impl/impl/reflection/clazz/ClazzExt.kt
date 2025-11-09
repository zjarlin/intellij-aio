package com.addzero.util.lsi_impl.impl.reflection.clazz

import com.addzero.util.lsi.constant.COLLECTION_TYPES
import com.addzero.util.lsi.constant.ENTITY_ANNOTATIONS
import com.addzero.util.lsi.constant.LOMBOK_ANNOTATIONS
import com.addzero.util.lsi.constant.TABLE_ANNOTATIONS
import com.addzero.util.lsi_impl.impl.reflection.kt.anno.guessTableNameOrNull
import site.addzero.util.str.toUnderLineCase
import java.lang.reflect.Modifier

fun Class<*>.guessTableName(): String {
    // 优先从注解获取表名
    val tableNameFromAnno = this.guessTableNameOrNull()
    return tableNameFromAnno ?: this.simpleName.toUnderLineCase()
}

fun Class<*>.getComponentType(): Class<*>? {
    return if (this.isArray) this.componentType else null
}

fun Class<*>.guessTableNameOrNull(): String? {
    val annotations = this.annotations
    return annotations.guessTableNameOrNull()
}


fun Class<*>.isCollectionType(): Boolean {
    return COLLECTION_TYPES.any { it.isAssignableFrom(this) }
}

fun Class<*>.isNullable(): Boolean {
    // 在Java中，只有基本类型不可为空
    return !this.isPrimitive
}

fun Class<*>.isPojo(): Boolean {
    // 排除接口和枚举
    if (this.isInterface || this.isEnum) {
        return false
    }
    // 排除抽象类（除非有实体注解）
    val isAbstract = Modifier.isAbstract(this.modifiers)

    val annotations = this.annotations.map { it.annotationClass.java.name }
    val hasEntityAnnotation = annotations.any { it in ENTITY_ANNOTATIONS }
    val hasTableAnnotation = annotations.any { it in TABLE_ANNOTATIONS }
    val hasLombokAnnotation = annotations.any { it in LOMBOK_ANNOTATIONS }

    // 如果是抽象类，只有带实体注解才认为是 POJO
    if (isAbstract) {
        return hasEntityAnnotation || hasTableAnnotation
    }

    // 非抽象类：有任何相关注解即可
    return hasEntityAnnotation || hasTableAnnotation || hasLombokAnnotation
}

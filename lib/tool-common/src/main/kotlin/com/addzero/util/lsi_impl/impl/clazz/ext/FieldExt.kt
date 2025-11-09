package com.addzero.util.lsi_impl.impl.clazz.ext

import com.addzero.util.lsi.constant.COLLECTION_TYPES
import java.lang.reflect.Field

fun Field.isStaticField(): Boolean {
    return java.lang.reflect.Modifier.isStatic(this.modifiers)
}

fun Field.isCollectionType(): Boolean {
    return COLLECTION_TYPES.any { it.isAssignableFrom(this.type) }
}

package com.addzero.util.lsi.assist

import com.addzero.util.lsi.constant.COLLECTION_TYPE_FQ_NAMES

fun String?.isCollectionType(): Boolean {
    this ?:return false
    return COLLECTION_TYPE_FQ_NAMES.any { this.startsWith(it) }
}

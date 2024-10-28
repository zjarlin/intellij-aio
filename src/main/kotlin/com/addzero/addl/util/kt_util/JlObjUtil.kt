package com.addzero.common.kt_util

import cn.hutool.core.collection.CollUtil
import cn.hutool.core.util.ObjUtil

fun Any?.isNull(): Boolean {
    return ObjUtil.isNull(this)
}

fun Any?.isNotNull(): Boolean {
    return ObjUtil.isNotNull(this)
}
fun Any?.isEmpty(): Boolean {
    return ObjUtil.isEmpty(this)
}
fun Any?.isNotEmpty(): Boolean {
    return ObjUtil.isNotEmpty(this)
}

fun Iterator<*>?.isEmpty(): Boolean {
    return CollUtil.isEmpty(this)
}
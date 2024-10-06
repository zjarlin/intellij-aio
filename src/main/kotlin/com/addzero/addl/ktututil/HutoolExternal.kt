package com.addzero.addl.ktututil

import cn.hutool.core.util.StrUtil
import com.alibaba.fastjson2.JSON
import org.apache.commons.lang3.StringUtils

fun CharSequence.toCamelCase(): String {
    return StrUtil.toCamelCase(this)
}

fun CharSequence.toUnderlineCase(): String {
    val toUnderlineCase = StrUtil.toUnderlineCase(this)
    return toUnderlineCase
}

fun <T> String.equalsIgnoreCase(string: String): Boolean {
    return StrUtil.equalsIgnoreCase(this, string)
}

fun String.upperCase(): String {
    return StringUtils.upperCase(this)
}

fun Any.toJson(): String {
    return JSON.toJSONString(this)
}

fun <T> String.parseObject(clazz: Class<T>): T {
    return JSON.parseObject(this, clazz)
}
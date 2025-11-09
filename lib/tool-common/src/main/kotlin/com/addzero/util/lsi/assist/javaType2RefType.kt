package com.addzero.util.lsi.assist

import com.intellij.util.ui.DialogUtil
import site.addzero.util.str.containsAny
import site.addzero.util.str.ignoreCaseIn
import site.addzero.util.str.ignoreCaseLike
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.*

internal fun javaType2RefType(javaType: String): String? {
    val s = when {
        javaType ignoreCaseLike "int" -> Int::class.java.name
        javaType ignoreCaseLike "long" -> Long::class.java.name
        javaType ignoreCaseLike "double" -> Double::class.java.name
        javaType ignoreCaseLike "float" -> Float::class.java.name
        javaType ignoreCaseLike "boolean" -> Boolean::class.java.name
        javaType ignoreCaseLike "string" -> String::class.java.name
        javaType ignoreCaseLike "date" -> Date::class.java.name
        javaType ignoreCaseLike "time" -> LocalTime::class.java.name
        javaType ignoreCaseLike "timezone" -> ZoneId::class.java.name
        javaType ignoreCaseLike "datetime" -> LocalDateTime::class.java.name
        else -> findRefType(javaType)
    }
    return s
}

internal fun findRefType(javaType: String): String? {
    val javaClass = fieldMappings.find {
        val equalsIgnoreCase = it.javaClassSimple.equalsIgnoreCase(javaType)
        equalsIgnoreCase
    }?.javaClassRef
    val containsAny = javaType.containsAny("Clob", "Object")
    val b = javaType ignoreCaseIn listOf("clob", "object")
    if (containsAny || b) {
        return String::class.java.name
    }
    if (javaClass == null) {
        DialogUtil.showWarningMsg("未找到java类型${javaType} 的映射关系,请联系作者适配")
        return String::class.java.name
    }
    return javaClass
}

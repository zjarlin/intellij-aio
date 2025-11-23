package site.addzero.util.lsi.assist

import site.addzero.util.str.containsAny
import site.addzero.util.str.ignoreCaseIn
import site.addzero.util.str.ignoreCaseLike
import java.math.BigDecimal
import java.time.LocalDate
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
        javaType ignoreCaseLike "localdate" -> LocalDate::class.java.name
        javaType ignoreCaseLike "time" -> LocalTime::class.java.name
        javaType ignoreCaseLike "timezone" -> ZoneId::class.java.name
        javaType ignoreCaseLike "datetime" -> LocalDateTime::class.java.name
        javaType ignoreCaseLike "localdatetime" -> LocalDateTime::class.java.name
        javaType ignoreCaseLike "bigdecimal" -> BigDecimal::class.java.name
        else -> findRefType(javaType)
    }
    return s
}

internal fun findRefType(javaType: String): String? {
    // Handle special cases
    val containsAny = javaType.containsAny("Clob", "Object")
    val b = javaType ignoreCaseIn listOf("clob", "object")
    if (containsAny || b) {
        return String::class.java.name
    }
    
    // Default to String for unknown types
    return String::class.java.name
}

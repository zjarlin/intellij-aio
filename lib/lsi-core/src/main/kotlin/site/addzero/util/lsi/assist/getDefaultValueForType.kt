package site.addzero.util.lsi.assist

import java.util.*

/**
 * 根据类型名称返回默认值的字符串表示
 * 这是一个便捷函数，内部调用 getDefaultAnyValueForType 并转换为字符串
 *
 * @param typeName 类型名称，可以是简单名称（如 "String", "Int"）或全限定名（如 "java.lang.String"）
 * @return 默认值的字符串表示
 */
fun getDefaultValueForType(typeName: String): String {
    return getDefaultAnyValueForType(typeName).toString()
}

/**
 * 根据类型名称返回对应的默认值对象
 * 支持：
 * - Java 基本类型和包装类型：int, Integer, long, Long, double, Double, float, Float, boolean, Boolean, byte, Byte, short, Short, char, Character
 * - Kotlin 基本类型：Int, Boolean, Byte, Char, Double, Float, Long, Short
 * - 常用类型：String, Date, LocalDate, LocalDateTime, BigDecimal
 * - 全限定名：java.lang.Integer, java.util.Date 等
 *
 * @param typeName 类型名称（不区分大小写，支持简单名称和全限定名）
 * @return 类型对应的默认值对象
 */
fun getDefaultAnyValueForType(typeName: String): Any {
    // 首先尝试全限定名匹配（精确匹配，区分大小写）
    val fqnResult = getDefaultValueForFqName(typeName)
    if (fqnResult != null) {
        return fqnResult
    }

    // 提取简单类型名（去掉包名）
    val simpleTypeName = typeName.substringAfterLast('.')

    // 使用简单类型名进行匹配（不区分大小写）
    return getDefaultValueForSimpleName(simpleTypeName)
}

/**
 * 根据全限定名返回默认值
 * 只处理需要特殊处理的全限定类型
 *
 * @param fqName 全限定类型名
 * @return 默认值对象，如果不是已知的全限定名则返回 null
 */
private fun getDefaultValueForFqName(fqName: String): Any? {
    return when (fqName) {
        // Java 包装类型
        "java.lang.Integer", "java.lang.Long" -> 0
        "java.lang.Double", "java.lang.Float" -> 0.0
        "java.lang.Boolean" -> false
        "java.lang.Byte" -> 0.toByte()
        "java.lang.Short" -> 0.toShort()
        "java.lang.Character" -> ' '
        "java.lang.String" -> ""

        // 日期时间类型
        "java.util.Date" -> Date().time
        "java.time.LocalDate" -> "2024-03-22"
        "java.time.LocalDateTime" -> "2024-03-22 12:00:00"
        "java.sql.Date" -> Date().time
        "java.sql.Timestamp" -> Date().time

        // 数学类型
        "java.math.BigDecimal" -> "0.00"
        "java.math.BigInteger" -> "0"

        else -> null
    }
}

/**
 * 根据简单类型名返回默认值
 * 使用不区分大小写的匹配
 *
 * @param simpleTypeName 简单类型名（不含包名）
 * @return 默认值对象
 */
private fun getDefaultValueForSimpleName(simpleTypeName: String): Any {
    val lowerCase = simpleTypeName.lowercase()

    return when (lowerCase) {
        // Java/Kotlin 整数类型
        "int", "integer" -> 0
        "long" -> 0L
        "short" -> 0.toShort()
        "byte" -> 0.toByte()

        // Java/Kotlin 浮点类型
        "float" -> 0.0f
        "double" -> 0.0

        // Java/Kotlin 布尔类型
        "boolean" -> false

        // Java/Kotlin 字符类型
        "char", "character" -> ' '

        // 字符串类型
        "string" -> ""

        // 日期时间类型
        "date" -> Date().time
        "localdate" -> "2024-03-22"
        "localdatetime" -> "2024-03-22 12:00:00"
        "timestamp" -> Date().time

        // 数学类型
        "bigdecimal" -> "0.00"
        "biginteger" -> "0"

        // 对于未知类型，返回类型名本身
        else -> simpleTypeName
    }
}

/**
 * 根据类型名称判断是否为基本数值类型
 *
 * @param typeName 类型名称
 * @return 如果是数值类型返回 true，否则返回 false
 */
fun isNumericType(typeName: String): Boolean {
    val simpleTypeName = typeName.substringAfterLast('.').lowercase()
    return simpleTypeName in setOf(
        "int", "integer", "long", "short", "byte",
        "float", "double", "bigdecimal", "biginteger"
    )
}

/**
 * 根据类型名称判断是否为布尔类型
 *
 * @param typeName 类型名称
 * @return 如果是布尔类型返回 true，否则返回 false
 */
fun isBooleanType(typeName: String): Boolean {
    val simpleTypeName = typeName.substringAfterLast('.').lowercase()
    return simpleTypeName == "boolean"
}

/**
 * 根据类型名称判断是否为字符串类型
 *
 * @param typeName 类型名称
 * @return 如果是字符串类型返回 true，否则返回 false
 */
fun isStringType(typeName: String): Boolean {
    val simpleTypeName = typeName.substringAfterLast('.').lowercase()
    return simpleTypeName == "string"
}

/**
 * 根据类型名称判断是否为日期时间类型
 *
 * @param typeName 类型名称
 * @return 如果是日期时间类型返回 true，否则返回 false
 */
fun isDateTimeType(typeName: String): Boolean {
    val simpleTypeName = typeName.substringAfterLast('.').lowercase()
    return simpleTypeName in setOf("date", "localdate", "localdatetime", "timestamp")
}

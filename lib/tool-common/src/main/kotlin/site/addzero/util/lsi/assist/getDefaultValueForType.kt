package site.addzero.util.lsi.assist

/**
 * Helper: 根据类型返回默认值
 */
fun getDefaultValueForType(typeName: String): String {
    val toString = getDefaultAnyValueForType(typeName).toString()
    return toString
}

fun getDefaultAnyValueForType(typeName: String): Any {
    val lowercase = typeName.lowercase()
    val any = when (lowercase) {
        "byte" -> 1.toByte()
        "short" -> 0.toShort()
        "int" -> "0"
        "long" -> "0L"
        "float" -> "0.0f"
        "double" -> "0.0"
        "boolean" -> "true"
        "string" -> "\"\""
        else -> typeName
    }
    return any

}



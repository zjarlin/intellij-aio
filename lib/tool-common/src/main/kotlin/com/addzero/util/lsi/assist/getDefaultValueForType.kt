package com.addzero.util.lsi.assist

/**
 * Helper: 根据类型返回默认值
 */
fun getDefaultValueForType(typeName: String): String {
    return when (typeName) {
        "Int" -> "0"
        "Boolean" -> "true"
        "Double" -> "0.0"
        "Float" -> "0.0f"
        "Long" -> "0L"
        "String" -> "\"\""
        else -> "\"\""
    }
}

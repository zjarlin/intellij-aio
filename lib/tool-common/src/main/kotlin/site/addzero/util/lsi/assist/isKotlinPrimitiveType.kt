package site.addzero.util.lsi.assist

internal fun isKotlinPrimitiveType(simpleTypeName: String): Boolean {
    return simpleTypeName in setOf("Int", "Boolean", "Byte", "Char", "Double", "Float", "Long", "Short")
}

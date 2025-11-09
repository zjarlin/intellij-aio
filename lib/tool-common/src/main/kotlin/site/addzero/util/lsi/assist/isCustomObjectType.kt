package site.addzero.util.lsi.assist

/**
 * Helper: 检查是否是自定义对象类型
 */
fun isCustomObjectType(typeName: String): Boolean {
    return typeName !in listOf("Int", "Boolean", "Double", "Float", "Long", "String", "Any")
}

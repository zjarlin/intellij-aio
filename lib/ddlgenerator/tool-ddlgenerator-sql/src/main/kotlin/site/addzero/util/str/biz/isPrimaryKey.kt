package site.addzero.util.str.biz

/**
 * 用columnName.来测试
 * @return [String]
 */
fun String?.isPrimaryKey(idName: String): String {
    if (this?.lowercase()!! == idName) {
        return "Y"
    }
    return ""
}

fun String?.isPrimaryKeyBoolean(idName: String): String {
    return if (isPrimaryKey(idName) == "Y") "Y" else ""
}

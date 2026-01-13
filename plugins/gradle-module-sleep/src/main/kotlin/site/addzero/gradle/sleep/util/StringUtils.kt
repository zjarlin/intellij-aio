package site.addzero.gradle.sleep.util

/**
 * 字符串工具类
 */
object StringUtils {

    /**
     * 将字符串转换为 camelCase 格式
     * 支持分隔符: `-`, `_`, `.`, 空格
     *
     * 示例:
     * - "tool-awt" -> "toolAwt"
     * - "tool_awt" -> "toolAwt"
     * - "tool.awt" -> "toolAwt"
     * - "kotlin-stdlib" -> "kotlinStdlib"
     */
    fun String.toCamelCaseByDelimiters(delimiters: CharArray = charArrayOf('-', '_', '.', ' ')): String {
        if (this.isEmpty()) return this

        val words = this.split(*delimiters)
        if (words.isEmpty()) return this

        return buildString {
            append(words[0].lowercase())
            for (i in 1 until words.size) {
                val word = words[i]
                if (word.isNotEmpty()) {
                    append(word.replaceFirstChar { it.uppercase() })
                }
            }
        }
    }

    /**
     * 将 camelCase 转换为 kebab-case
     *
     * 示例:
     * - "toolAwt" -> "tool-awt"
     * - "kotlinStdlib" -> "kotlin-stdlib"
     */
    fun String.toKebabCase(): String {
        return this.replace(Regex("([a-z])([A-Z])"), "$1-$2").lowercase()
    }
}

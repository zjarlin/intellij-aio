package com.addzero.addl.util

import cn.hutool.core.io.FileUtil
import cn.hutool.core.util.StrUtil
import cn.hutool.extra.pinyin.PinyinUtil
import com.addzero.addl.util.JlStrUtil.extractTextBetweenPTags
import com.addzero.addl.util.JlStrUtil.ignoreCaseIn
import com.addzero.addl.util.JlStrUtil.ignoreCaseNotIn
import com.addzero.common.kt_util.isBlank
import java.util.*

/**
 * @author zjarlin
 * @since 2023/3/17 13:12
 */
fun main() {
    val excludeColumns = listOf("ID", "CREATE_BY", "UPDATE_BY", "CREATE_TIME", "UPDATE_TIME")

    // 示例数据类
    data class ColumnDto(
        val colName: String,
        val comment: String,
    )
    // 使用中缀函数
    println("id" ignoreCaseIn excludeColumns)      // 输出: true
    println("name" ignoreCaseIn excludeColumns)    // 输出: false
    println("id" ignoreCaseNotIn excludeColumns)   // 输出: false
    println("name" ignoreCaseNotIn excludeColumns) // 输出: true

    // 在过滤中使用
    val dto = listOf(
        ColumnDto("ID", "主键"), ColumnDto("name", "姓名"), ColumnDto("age", "年龄")
    )

    // 过滤不包含的列
    val filtered = dto.filter { it.colName ignoreCaseNotIn excludeColumns }

    // 过滤包含的列
    val included = dto.filter { it.colName ignoreCaseIn excludeColumns }
}

object JlStrUtil {

    /**
     * 检查字符串是否不在列表中（不区分大小写）
     */
    infix fun String.ignoreCaseNotIn(collection: Collection<String>): Boolean {
        val b = this ignoreCaseIn collection
        return !b
    }

    /**
     * 检查字符串是否在列表中（不区分大小写）
     */
    infix fun String.ignoreCaseIn(collection: Collection<String>): Boolean = collection.any { it.equals(this, ignoreCase = true) }

    infix fun String.ignoreCaseLike(other: String): Boolean {
        return this.contains(other, ignoreCase = true)
    }



    @JvmStatic
    fun main(args: Array<String>) {
        val listOf = listOf("id", "name", "age")
        val b = "id" ignoreCaseIn listOf
        println(b)

    }

    /**
     * 删除字符串中最后一次出现的指定字符。
     *
     * @param str 字符串
     * @param ch 要删除的字符
     * @return 删除指定字符后的字符串
     */
    fun removeLastCharOccurrence(str: String, ch: Char): String {
        if (StrUtil.isBlank(str)) {
            return ""
        }

        val lastIndex = str.lastIndexOf(ch) // 获取指定字符最后一次出现的位置
        return if (lastIndex != -1) {
            // 如果找到了指定字符，则删除它
            str.substring(0, lastIndex) + str.substring(lastIndex + 1)
        } else {
            // 如果没有找到指定字符，则返回原字符串
            str!!
        }
    }


    fun makeSurroundWith(s: String, fix: String): String {
        // 如果 s 为空或全是空白字符，则直接返回空字符串
        if (s.isBlank()) {
            return ""
        }
        val s1 = StrUtil.addPrefixIfNot(s, fix)
        val s2 = StrUtil.addSuffixIfNot(s1, fix)
        return s2
    }

    fun removeNotChinese(str: String): String {
        if (StrUtil.isBlank(str)) {
            return ""
        }
        val regex = "[^\u4E00-\u9FA5]"
        val s1 = str.replace(regex.toRegex(), "")
        return s1
    }

    /**
     * 优化表名
     * @param tableEnglishName
     * @param tableChineseName
     * @return [String]
     */
    fun shortEng(tableEnglishName: String, tableChineseName: String?): String {
        var tableEnglishName = tableEnglishName
        if (StrUtil.length(tableEnglishName) > 15) {
            tableEnglishName = PinyinUtil.getFirstLetter(tableChineseName, "")
        }
        tableEnglishName = StrUtil.removeAny(tableEnglishName, "(", ")")
        tableEnglishName = tableEnglishName.replace("\\((.*?)\\)".toRegex(), "") // 移除括号及其内容
        tableEnglishName = tableEnglishName.replace("(_{2,})".toRegex(), "_") // 移除连续的下划线
        return tableEnglishName
    }

    /**
     * 删除多余符号
     * @param [source]
     * @param [duplicateElement]
     * @return [String?]
     */
    fun removeDuplicateSymbol(source: String, duplicateElement: String): String? {
        if (Objects.isNull(source) || source.isEmpty() || Objects.isNull(duplicateElement) || duplicateElement.isEmpty()) {
            return source
        }
        val sb = StringBuilder()
        var previous = "" // 初始化前一个元素，用于比较
        var i = 0
        while (i < source.length) {
            val elementLength = duplicateElement.length
            if (i + elementLength <= source.length && source.substring(i, i + elementLength) == duplicateElement) {
                if (previous != duplicateElement) {
                    sb.append(duplicateElement)
                    previous = duplicateElement
                }
                i += elementLength
            } else {
                sb.append(source[i])
                previous = source[i].toString()
                i++
            }
        }
        return sb.toString()
    }

    /**
     *标记为代码块
     * @param [markdown]
     * @return [String]
     */
    fun extractMarkdownBlockContent(markdown: String): String {
        if (markdown.containsAny("json", "```")) {
            val regex = Regex("```\\w*\\s*(.*?)\\s*```", RegexOption.DOT_MATCHES_ALL)
            val matchResult = regex.find(markdown)
            return matchResult?.groups?.get(1)?.value?.trim() ?: ""
        }

        return markdown
    }

    /**
     * 提取p标签环绕的字符串
     * @param [input]
     * @return [List<String>]
     */
    fun extractTextBetweenPTags(input: String): List<String> {
        // 判断字符串是否包含 <p> 标签
        if ("<p>" !in input || "</p>" !in input) {
            return emptyList() // 如果没有 <p> 标签，返回空列表
        }

        // 定义正则表达式，用来匹配 <p> 和 </p> 之间的内容
        val regex = Regex("<p>(.*?)</p>")

        // 提取所有匹配的内容
        return regex.findAll(input).map { it.groupValues[1] }.toList()
    }

    /**
     * 变量名类型
     */
    enum class VariableType {
        /** 常量: 大写+下划线 (如: MAX_VALUE) */
        CONSTANT,
        /** 小驼峰变量名 (如: firstName) */
        CAMEL_CASE,
        /** ���驼峰类名 (如: UserInfo) */
        PASCAL_CASE,
        /** 下划线分隔 (如: user_name) */
        SNAKE_CASE,
        /** 中划线分隔 (如: user-name) */
        KEBAB_CASE
    }

    /**
     * 生成合法的变量名
     * @param input 输入字符串
     * @param type 变量名类型
     * @param prefix 前缀(可选)
     * @param suffix 后缀(可选)
     * @return 处理后的变量名
     */
    fun toValidVariableName(
        input: String,
        type: VariableType = VariableType.CAMEL_CASE,
        prefix: String = "",
        suffix: String = ""
    ): String {
        if (input.isBlank()) return ""

        // 检查是否为纯数字
        if (input.all { it.isDigit() }) {
            return "__${input}"  // 纯数字加双下划线前缀
        }

        // 1. 清理特殊字符，只保留字母、数字、空格、下划线、中划线
        var result = input.replace(Regex("[^a-zA-Z0-9\\s_-]"), "")

        if (result.isBlank()) {
            return input
        }
        // 2. 处理数字开头的情况
        if (result.first().isDigit()) {
            result = "_$result"
        }

        // 3. 分词处理（按空格、下划线、中划线分割）
        val words = result.split(Regex("[\\s_-]+"))
            .filter { it.isNotBlank() }
            .map { it.lowercase() }

        // 4. 根据类型格式化
        result = when (type) {
            VariableType.CONSTANT -> {
                words.joinToString("_") { it.uppercase() }
            }
            VariableType.CAMEL_CASE -> {
                words.first() + words.drop(1)
                    .joinToString("") { it.capitalize() }
            }
            VariableType.PASCAL_CASE -> {
                words.joinToString("") { it.capitalize() }
            }
            VariableType.SNAKE_CASE -> {
                words.joinToString("_") { it.lowercase() }
            }
            VariableType.KEBAB_CASE -> {
                words.joinToString("-") { it.lowercase() }
            }
        }

        // 5. 添加前缀和后缀
        if (prefix.isNotBlank()) {
            result = when (type) {
                VariableType.CONSTANT -> "${prefix.uppercase()}_$result"
                VariableType.CAMEL_CASE -> prefix.lowercase() + result.capitalize()
                VariableType.PASCAL_CASE -> prefix.capitalize() + result
                VariableType.SNAKE_CASE -> "${prefix.lowercase()}_$result"
                VariableType.KEBAB_CASE -> "${prefix.lowercase()}-$result"
            }
        }

        if (suffix.isNotBlank()) {
            result = when (type) {
                VariableType.CONSTANT -> "${result}_${suffix.uppercase()}"
                VariableType.CAMEL_CASE -> result + suffix.capitalize()
                VariableType.PASCAL_CASE -> result + suffix.capitalize()
                VariableType.SNAKE_CASE -> "${result}_${suffix.lowercase()}"
                VariableType.KEBAB_CASE -> "${result}-${suffix.lowercase()}"
            }
        }

        return result
    }

    // 只使用扩展函数形式
    fun String.toConstantName(prefix: String = "", suffix: String = "") =
        toValidVariableName(this, VariableType.CONSTANT, prefix, suffix)

    fun String.toCamelCase(prefix: String = "", suffix: String = "") =
        toValidVariableName(this, VariableType.CAMEL_CASE, prefix, suffix)

    fun String.toPascalCase(prefix: String = "", suffix: String = "") =
        toValidVariableName(this, VariableType.PASCAL_CASE, prefix, suffix)

    fun String.toSnakeCase(prefix: String = "", suffix: String = "") =
        toValidVariableName(this, VariableType.SNAKE_CASE, prefix, suffix)

    fun String.toKebabCase(prefix: String = "", suffix: String = "") =
        toValidVariableName(this, VariableType.KEBAB_CASE, prefix, suffix)

    private fun String.capitalize() =
        replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

fun CharSequence.containsAny(vararg testStrs: CharSequence): Boolean {
    val containsAny = StrUtil.containsAny(this, *testStrs)
    return containsAny
}

fun CharSequence.removeAny(vararg testStrs: CharSequence): String {
    if (this.isBlank()) {
        return ""
    }
    return StrUtil.removeAny(this, *testStrs)?:""
}

fun String.getParentPathAndmkdir(childPath: String): String {
    val parent1 = FileUtil.getParent(this, 1)
    //            val parent2 = FileUtil.getParent(filePath, 2)
    //            val parent3 = FileUtil.getParent(filePath, 0)
    val mkParentDirs = FileUtil.mkdir("$parent1/$childPath")
    //            val canonicalPath = virtualFile.canonicalPath
    //            val parent = psiFile!!.parent
    val filePath1 = mkParentDirs.path
    return filePath1
}

fun CharSequence.removeAnyQuote(): String {
    if (this.isBlank()) {
        return ""
    }
    return StrUtil.removeAny(this, "\"","\\")
}




fun String?.extractMarkdownBlockContent(): String {
    if (this.isBlank()) {
        return ""
    }

    if (this!!.containsAny("json", "```")) {
        val regex = Regex("```\\w*\\s*(.*?)\\s*```", RegexOption.DOT_MATCHES_ALL)
        val matchResult = regex.find(this)
        return matchResult?.groups?.get(1)?.value?.trim() ?: ""
    }

    val extractTextBetweenPTags = extractTextBetweenPTags(this)
    val firstOrNull = extractTextBetweenPTags.firstOrNull()

    val s = firstOrNull ?: this
    s.removeAny("\"")
    return s

}

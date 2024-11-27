package com.addzero.addl.util

import cn.hutool.core.io.FileUtil
import cn.hutool.core.util.StrUtil
import cn.hutool.extra.pinyin.PinyinUtil
import com.addzero.addl.util.JlStrUtil.ignoreCaseIn
import com.addzero.addl.util.JlStrUtil.ignoreCaseNotIn
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
        val comment: String
    )
    // 使用中缀函数
    println("id" ignoreCaseIn excludeColumns)      // 输出: true
    println("name" ignoreCaseIn excludeColumns)    // 输出: false
    println("id" ignoreCaseNotIn excludeColumns)   // 输出: false
    println("name" ignoreCaseNotIn excludeColumns) // 输出: true

    // 在过滤中使用
    val dto = listOf(
        ColumnDto("ID", "主键"),
        ColumnDto("name", "姓名"),
        ColumnDto("age", "年龄")
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
    infix fun String.ignoreCaseIn(collection: Collection<String>): Boolean =
        collection.any { it.equals(this, ignoreCase = true) }

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


}

fun CharSequence.containsAny(vararg testStrs: CharSequence): Boolean {
    val containsAny = StrUtil.containsAny(this, *testStrs)
    return containsAny
}
fun CharSequence.removeAny(vararg testStrs: CharSequence): String? {
    if (this.isBlank()) {
        return ""
    }
    return StrUtil.removeAny(this, *testStrs)
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
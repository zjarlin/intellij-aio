package com.addzero.common.kt_util

import cn.hutool.core.text.CharSequenceUtil
import cn.hutool.core.util.StrUtil
import com.intellij.openapi.util.NlsSafe
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

val loggerMap = ConcurrentHashMap<Class<*>, Logger>()
inline val <reified T> T.log: Logger get() = loggerMap.computeIfAbsent(T::class.java) { LoggerFactory.getLogger(it) }

object JlStrUtil {
    fun <T> groupBySeparator(lines: List<T>, predicate: (T) -> Boolean): Map<T, List<T>> {
        val separatorIndices = lines.indices.filter { predicate(lines[it]) }
        return separatorIndices.mapIndexed { index, spe ->
            val next = if (index + 1 < separatorIndices.size) {
                separatorIndices[index + 1]
            } else {
                lines.size // 如果没有下一个分隔符，取行的总数
            }

            val subList = lines.subList(spe + 1, next)
            lines[spe] to subList // 使用 Pair 进行配对
        }.toMap()
    }

//    /**
//     *提取markdown代码块中的内容
//     * @param [markdown]
//     * @return [String]
//     */
//    fun extractMarkdownBlockContent(markdown: String): String {
//        val regex = Regex("```\\w*\\s*(.*?)\\s*```", RegexOption.DOT_MATCHES_ALL)
//        val matchResult = regex.find(markdown)
//        return matchResult?.groups?.get(1)?.value?.trim() ?: ""
//    }

}

fun String?.isBlank(): Boolean {
    return StrUtil.isBlank(this)
}

fun String?.isNotBlank(): Boolean {
    return StrUtil.isNotBlank(this)
}

/**
 * 扩展函数：移除重复符号
 */
fun String?.removeDuplicateSymbol(duplicateElement: String): String {
    if (this.isNullOrEmpty() || duplicateElement.isEmpty()) {
        return this ?: ""
    }

    val sb = StringBuilder()
    var previous = "" // 初始化前一个元素，用于比较
    var i = 0

    while (i < this.length) {
        val elementLength = duplicateElement.length
        if (i + elementLength <= this.length && this.substring(i, i + elementLength) == duplicateElement) {
            if (previous != duplicateElement) {
                sb.append(duplicateElement)
                previous = duplicateElement
            }
            i += elementLength
        } else {
            sb.append(this[i])
            previous = this[i].toString()
            i++
        }
    }
    return sb.toString()
}

/**
 * 扩展函数：清理多余的char
 */
fun String?.removeDuplicateSymbol(symbol: Char): String {
    if (StrUtil.isBlank(this)) {
        return ""
    }
    val sb = StringBuilder()
    var prevIsSymbol = false

    for (c in this!!.toCharArray()) {
        if (c == symbol) {
            if (!prevIsSymbol) {
                sb.append(c)
                prevIsSymbol = true
            }
        } else {
            sb.append(c)
            prevIsSymbol = false
        }
    }
    return sb.toString()
}

/**
 * 扩展函数：提取路径部分
 */
fun String.getPathFromRight(n: Int): String? {
    val parts = this.split(".").filter { it.isNotEmpty() }

    if (parts!!.size < n) {
        return this // 输入字符串中的路径部分不足n个，返回整个输入字符串
    }

    return parts.dropLast(n).joinToString(".")
}

/**
 * 扩展函数：添加前后缀
 */
fun String?.makeSurroundWith(fix: String?): String? {
    return this?.let { StrUtil.addPrefixIfNot(it, fix).let { StrUtil.addSuffixIfNot(it, fix) } }
}

/**
 * 扩展函数：提取REST URL
 */
fun String?.getRestUrl(): String {
    if (CharSequenceUtil.isBlank(this)) {
        return ""
    }
    val pattern = Pattern.compile(".*:\\d+(/[^/]+)(/.*)")
    val matcher = pattern.matcher(this)

    return if (matcher.matches() && matcher.groupCount() > 1) {
        matcher.group(2)
    } else {
        ""
    }
}

/**
 * 扩展函数：用HTML P标签包裹
 */
fun String?.makeSurroundWithHtmlP(): String? {
    if (StrUtil.isBlank(this)) {
        return ""
    }
    return StrUtil.addPrefixIfNot(this, "<p>").let { StrUtil.addSuffixIfNot(it, "</p>") }
}

/**
 * 扩展函数：检查是否包含中文
 */
fun String?.containsChinese(): Boolean {
    if (StrUtil.isBlank(this)) {
        return false
    }
    val pattern = Pattern.compile("[\\u4e00-\\u9fa5]")
    val matcher = pattern.matcher(this)
    return matcher.find()
}

fun String?.lowerCase(): String {
    if (StrUtil.isBlank(this)) {
        return ""
    }
    val lowerCase = this.lowerCase()
    return lowerCase

}

fun String?.cleanBlank(): String {
    if (StrUtil.isBlank(this)) {
        return ""
    }
    return StrUtil.cleanBlank(this)

}

fun String?.addPrefixIfNot(prefix: @NlsSafe String?): String {
    if (StrUtil.isBlank(this)) {
        return ""
    }
    return StrUtil.addPrefixIfNot(this, prefix)

}
fun String?.addSuffixIfNot(fix: String): String {
    if (StrUtil.isBlank(this)) {
        return ""
    }
    return StrUtil.addSuffixIfNot(this, fix)

}
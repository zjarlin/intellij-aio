package com.addzero.addl.action.enumgen

import com.addzero.addl.action.dictgen.DictItemInfo

fun parseDictItems(comment: String, dictId: String): List<DictItemInfo> {
    val items = mutableListOf<DictItemInfo>()

    // 移除注释符号和空白
    val cleanComment = comment.replace(Regex("/\\*\\*|\\*/|\\*"), "")
        .trim()
        .lines()
        .filter { it.isNotBlank() }

    for (line in cleanComment) {
        // 支持等号和冒号作为分隔符
        val separators = listOf("=", ":")
        val separator = separators.find { line.contains(it) } ?: continue

        val parts = line.split(separator, limit = 2)
        if (parts.size == 2) {
            val code = parts[0].trim()
            val description = parts[1].trim()
            items.add(DictItemInfo(dictId, code, description))
        }
    }

    return items
}
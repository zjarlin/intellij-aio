package site.addzero.koog.agent.settings

internal data class KoogAgentTomlDocument(
    val root: Map<String, String>,
    val tables: Map<String, Map<String, String>>,
) {
    fun table(path: String): Map<String, String> {
        return tables[path]
            ?: tables.entries.firstOrNull { (key, _) -> key.equals(path, ignoreCase = true) }?.value
            ?: emptyMap()
    }
}

internal object KoogAgentToml {
    fun parse(text: String): KoogAgentTomlDocument {
        val root = linkedMapOf<String, String>()
        val tables = linkedMapOf<String, MutableMap<String, String>>()
        var currentTable = ""

        text.lineSequence().forEach { rawLine ->
            val line = stripComment(rawLine).trim()
            when {
                line.isBlank() -> return@forEach
                line.startsWith("[") && line.endsWith("]") && !line.startsWith("[[") -> {
                    currentTable = line.removePrefix("[").removeSuffix("]").trim()
                    tables.getOrPut(currentTable) { linkedMapOf() }
                }

                else -> {
                    val separator = findAssignmentSeparator(line) ?: return@forEach
                    val key = line.substring(0, separator).trim()
                    val value = parseStringValue(line.substring(separator + 1).trim()) ?: return@forEach
                    putValue(root, tables, currentTable, key, value)
                }
            }
        }

        return KoogAgentTomlDocument(root = root, tables = tables)
    }

    private fun putValue(
        root: MutableMap<String, String>,
        tables: MutableMap<String, MutableMap<String, String>>,
        currentTable: String,
        key: String,
        value: String,
    ) {
        val keyPath = parseDottedPath(key)
        if (keyPath.isEmpty()) {
            return
        }

        if (keyPath.size == 1) {
            if (currentTable.isBlank()) {
                root[keyPath.single()] = value
            } else {
                tables.getOrPut(currentTable) { linkedMapOf() }[keyPath.single()] = value
            }
            return
        }

        val nestedTable = buildList {
            if (currentTable.isNotBlank()) {
                add(currentTable)
            }
            addAll(keyPath.dropLast(1))
        }.joinToString(".")
        tables.getOrPut(nestedTable) { linkedMapOf() }[keyPath.last()] = value
    }

    private fun parseDottedPath(key: String): List<String> {
        val result = mutableListOf<String>()
        val segment = StringBuilder()
        var inBasicString = false
        var inLiteralString = false
        var escaped = false

        key.forEach { char ->
            when {
                escaped -> {
                    segment.append(char)
                    escaped = false
                }

                inBasicString && char == '\\' -> escaped = true
                !inLiteralString && char == '"' -> inBasicString = !inBasicString
                !inBasicString && char == '\'' -> inLiteralString = !inLiteralString
                !inBasicString && !inLiteralString && char == '.' -> {
                    segment.toString().trim().unquoteTomlKey().takeIf { it.isNotBlank() }?.let(result::add)
                    segment.clear()
                }

                else -> segment.append(char)
            }
        }
        segment.toString().trim().unquoteTomlKey().takeIf { it.isNotBlank() }?.let(result::add)
        return result
    }

    private fun String.unquoteTomlKey(): String {
        val trimmed = trim()
        return when {
            trimmed.length >= 2 && trimmed.first() == '"' && trimmed.last() == '"' -> trimmed.substring(1, trimmed.lastIndex)
            trimmed.length >= 2 && trimmed.first() == '\'' && trimmed.last() == '\'' -> trimmed.substring(1, trimmed.lastIndex)
            else -> trimmed
        }
    }

    private fun stripComment(line: String): String {
        var inBasicString = false
        var inLiteralString = false
        var escaped = false

        line.forEachIndexed { index, char ->
            when {
                escaped -> escaped = false
                inBasicString && char == '\\' -> escaped = true
                !inLiteralString && char == '"' -> inBasicString = !inBasicString
                !inBasicString && char == '\'' -> inLiteralString = !inLiteralString
                !inBasicString && !inLiteralString && char == '#' -> return line.substring(0, index)
            }
        }
        return line
    }

    private fun findAssignmentSeparator(line: String): Int? {
        var inBasicString = false
        var inLiteralString = false
        var escaped = false

        line.forEachIndexed { index, char ->
            when {
                escaped -> escaped = false
                inBasicString && char == '\\' -> escaped = true
                !inLiteralString && char == '"' -> inBasicString = !inBasicString
                !inBasicString && char == '\'' -> inLiteralString = !inLiteralString
                !inBasicString && !inLiteralString && char == '=' -> return index
            }
        }
        return null
    }

    private fun parseStringValue(rawValue: String): String? {
        return when {
            rawValue.startsWith("\"") -> parseBasicString(rawValue)
            rawValue.startsWith("'") -> parseLiteralString(rawValue)
            else -> rawValue.trim().takeIf { it.isNotBlank() }
        }
    }

    private fun parseBasicString(rawValue: String): String? {
        val result = StringBuilder()
        var index = 1
        while (index < rawValue.length) {
            val char = rawValue[index++]
            when (char) {
                '"' -> return result.toString()
                '\\' -> {
                    if (index >= rawValue.length) {
                        return null
                    }
                    result.append(
                        when (val escaped = rawValue[index++]) {
                            'b' -> '\b'
                            't' -> '\t'
                            'n' -> '\n'
                            'f' -> '\u000C'
                            'r' -> '\r'
                            '"', '\\' -> escaped
                            else -> escaped
                        },
                    )
                }

                else -> result.append(char)
            }
        }
        return null
    }

    private fun parseLiteralString(rawValue: String): String? {
        val end = rawValue.indexOf('\'', startIndex = 1)
        if (end <= 0) {
            return null
        }
        return rawValue.substring(1, end)
    }
}

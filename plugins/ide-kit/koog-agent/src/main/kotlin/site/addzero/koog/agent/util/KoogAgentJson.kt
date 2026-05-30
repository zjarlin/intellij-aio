package site.addzero.koog.agent.util

internal object KoogAgentJson {
    fun stringify(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> quote(value)
            is Number, is Boolean -> value.toString()
            is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}") { (key, entryValue) ->
                "${quote(key.toString())}:${stringify(entryValue)}"
            }

            is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]") { item -> stringify(item) }
            is Array<*> -> value.joinToString(prefix = "[", postfix = "]") { item -> stringify(item) }
            else -> quote(value.toString())
        }
    }

    fun parse(text: String): Any? {
        return Parser(text).parse()
    }

    fun stringAt(
        root: Any?,
        vararg path: Any,
    ): String? {
        var current = root
        path.forEach { segment ->
            val node = current
            current = when {
                segment is String && node is Map<*, *> -> node[segment]
                segment is Int && node is List<*> -> node.getOrNull(segment)
                else -> return null
            }
        }
        return current as? String
    }

    fun findFirstStringByKey(
        root: Any?,
        key: String,
    ): String? {
        return findFirstStringByKeys(root, setOf(key))
    }

    fun findFirstStringByKeys(
        root: Any?,
        keys: Collection<String>,
    ): String? {
        val normalizedKeys = keys.map { it.lowercase() }.toSet()
        return findFirstStringByKeys(root, normalizedKeys)
    }

    fun findTextBySiblingType(
        root: Any?,
        acceptedTypes: Set<String>,
    ): String? {
        return when (root) {
            is Map<*, *> -> {
                val type = root["type"] as? String
                val text = root["text"] as? String
                if (type in acceptedTypes && !text.isNullOrBlank()) {
                    return text
                }
                root.values.asSequence()
                    .mapNotNull { value -> findTextBySiblingType(value, acceptedTypes) }
                    .firstOrNull()
            }

            is List<*> -> root.asSequence()
                .mapNotNull { value -> findTextBySiblingType(value, acceptedTypes) }
                .firstOrNull()

            else -> null
        }
    }

    private fun findFirstStringByKeys(
        root: Any?,
        normalizedKeys: Set<String>,
    ): String? {
        return when (root) {
            is Map<*, *> -> {
                root.entries.firstNotNullOfOrNull { (key, value) ->
                    if (key.toString().lowercase() in normalizedKeys && value is String && value.isNotBlank()) {
                        value
                    } else {
                        null
                    }
                } ?: root.values.firstNotNullOfOrNull { value -> findFirstStringByKeys(value, normalizedKeys) }
            }

            is List<*> -> root.firstNotNullOfOrNull { value -> findFirstStringByKeys(value, normalizedKeys) }
            else -> null
        }
    }

    private fun quote(value: String): String {
        return buildString {
            append('"')
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> {
                        if (char.code < 0x20) {
                            append("\\u")
                            append(char.code.toString(16).padStart(4, '0'))
                        } else {
                            append(char)
                        }
                    }
                }
            }
            append('"')
        }
    }

    private class Parser(private val text: String) {
        private var index = 0

        fun parse(): Any? {
            skipWhitespace()
            val value = parseValue()
            skipWhitespace()
            if (index != text.length) {
                error("Unexpected trailing JSON token at $index")
            }
            return value
        }

        private fun parseValue(): Any? {
            skipWhitespace()
            if (index >= text.length) {
                error("Unexpected end of JSON")
            }
            return when (val char = text[index]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                '-', in '0'..'9' -> parseNumber()
                else -> error("Unexpected JSON token '$char' at $index")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            val result = linkedMapOf<String, Any?>()
            skipWhitespace()
            if (peek('}')) {
                index++
                return result
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                result[key] = parseValue()
                skipWhitespace()
                when {
                    peek(',') -> index++
                    peek('}') -> {
                        index++
                        return result
                    }

                    else -> error("Expected ',' or '}' at $index")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            val result = mutableListOf<Any?>()
            skipWhitespace()
            if (peek(']')) {
                index++
                return result
            }
            while (true) {
                result.add(parseValue())
                skipWhitespace()
                when {
                    peek(',') -> index++
                    peek(']') -> {
                        index++
                        return result
                    }

                    else -> error("Expected ',' or ']' at $index")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val result = StringBuilder()
            while (index < text.length) {
                val char = text[index++]
                when (char) {
                    '"' -> return result.toString()
                    '\\' -> {
                        if (index >= text.length) {
                            error("Unexpected end of JSON escape")
                        }
                        result.append(parseEscape(text[index++]))
                    }

                    else -> result.append(char)
                }
            }
            error("Unterminated JSON string")
        }

        private fun parseEscape(char: Char): Char {
            return when (char) {
                '"', '\\', '/' -> char
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> {
                    if (index + 4 > text.length) {
                        error("Invalid unicode escape")
                    }
                    val hex = text.substring(index, index + 4)
                    index += 4
                    hex.toInt(16).toChar()
                }

                else -> error("Invalid JSON escape '$char'")
            }
        }

        private fun parseNumber(): Number {
            val start = index
            if (peek('-')) {
                index++
            }
            while (index < text.length && text[index].isDigit()) {
                index++
            }
            if (peek('.')) {
                index++
                while (index < text.length && text[index].isDigit()) {
                    index++
                }
            }
            if (index < text.length && (text[index] == 'e' || text[index] == 'E')) {
                index++
                if (index < text.length && (text[index] == '+' || text[index] == '-')) {
                    index++
                }
                while (index < text.length && text[index].isDigit()) {
                    index++
                }
            }
            val raw = text.substring(start, index)
            return raw.toLongOrNull() ?: raw.toDouble()
        }

        private fun parseLiteral(
            literal: String,
            value: Any?,
        ): Any? {
            if (!text.startsWith(literal, index)) {
                error("Expected '$literal' at $index")
            }
            index += literal.length
            return value
        }

        private fun skipWhitespace() {
            while (index < text.length && text[index].isWhitespace()) {
                index++
            }
        }

        private fun expect(char: Char) {
            if (!peek(char)) {
                error("Expected '$char' at $index")
            }
            index++
        }

        private fun peek(char: Char): Boolean {
            return index < text.length && text[index] == char
        }
    }
}

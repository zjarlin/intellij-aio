package site.addzero.dioxus.buddy.model

object DioxusPreviewParser {
    private val functionRegex = Regex("""\bfn\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(""")
    private val attributeNameRegex = Regex("""#\s*\[\s*([A-Za-z_][A-Za-z0-9_:]*)""")
    private val supportedAttributes = setOf(
        "component",
        "dioxus_preview",
        "preview",
    )

    fun parse(text: String): List<ParsedDioxusPreviewTarget> {
        return functionRegex.findAll(text)
            .mapNotNull { functionMatch ->
                val attributes = collectAdjacentOuterAttributes(text, functionMatch.range.first)
                val supported = attributes.names
                    .map { it.substringAfterLast("::") }
                    .filter { it in supportedAttributes }
                if (supported.isEmpty()) {
                    return@mapNotNull null
                }

                val openParenOffset = functionMatch.range.last
                val closeParenOffset = findMatching(text, openParenOffset, '(', ')') ?: return@mapNotNull null
                val bodyOpenOffset = findNextNonWhitespace(text, closeParenOffset + 1)
                    ?.takeIf { text[it] == '{' }
                    ?: findBodyOpenAfterSignature(text, closeParenOffset + 1)
                    ?: return@mapNotNull null
                val bodyCloseOffset = findMatching(text, bodyOpenOffset, '{', '}') ?: return@mapNotNull null
                val nameGroup = functionMatch.groups[1] ?: return@mapNotNull null

                ParsedDioxusPreviewTarget(
                    functionName = nameGroup.value,
                    attributeNames = supported.distinct(),
                    attributeOffset = attributes.startOffset,
                    functionOffset = functionMatch.range.first,
                    functionEndOffset = bodyCloseOffset + 1,
                    nameOffset = nameGroup.range.first,
                    parameterText = text.substring(openParenOffset + 1, closeParenOffset),
                )
            }
            .toList()
    }

    fun findTargetAt(
        text: String,
        offset: Int,
    ): ParsedDioxusPreviewTarget? {
        return parse(text)
            .filter { target -> offset in target.attributeOffset..target.functionEndOffset }
            .minByOrNull { target -> target.functionEndOffset - target.attributeOffset }
    }

    private fun collectAdjacentOuterAttributes(
        text: String,
        functionOffset: Int,
    ): AttributeBlock {
        var cursor = functionOffset
        val names = mutableListOf<String>()
        var startOffset = functionOffset

        while (true) {
            val lineStart = text.lastIndexOf('\n', cursor - 1).let { if (it == -1) 0 else it + 1 }
            val previousLineEnd = lineStart - 1
            if (previousLineEnd <= 0) {
                break
            }
            val previousLineStart = text.lastIndexOf('\n', previousLineEnd - 1).let { if (it == -1) 0 else it + 1 }
            val line = text.substring(previousLineStart, previousLineEnd).trim()
            if (line.isBlank()) {
                cursor = previousLineStart
                continue
            }
            if (!line.startsWith("#[")) {
                break
            }
            attributeNameRegex.find(line)?.groupValues?.getOrNull(1)?.let(names::add)
            startOffset = previousLineStart
            cursor = previousLineStart
        }

        return AttributeBlock(
            names = names.asReversed(),
            startOffset = startOffset,
        )
    }

    private fun findBodyOpenAfterSignature(
        text: String,
        startOffset: Int,
    ): Int? {
        var index = startOffset
        while (index < text.length) {
            when (text[index]) {
                '{' -> return index
                ';' -> return null
                '\n', '\r', '\t', ' ', '-', '>', ':' -> index++
                else -> index++
            }
        }
        return null
    }

    private fun findNextNonWhitespace(
        text: String,
        startOffset: Int,
    ): Int? {
        var index = startOffset
        while (index < text.length) {
            if (!text[index].isWhitespace()) {
                return index
            }
            index++
        }
        return null
    }

    private fun findMatching(
        text: String,
        openOffset: Int,
        open: Char,
        close: Char,
    ): Int? {
        if (openOffset !in text.indices || text[openOffset] != open) {
            return null
        }
        var depth = 0
        var index = openOffset
        var stringDelimiter: Char? = null
        var escaped = false
        var lineComment = false
        var blockCommentDepth = 0

        while (index < text.length) {
            val char = text[index]
            val next = text.getOrNull(index + 1)

            if (lineComment) {
                if (char == '\n') lineComment = false
                index++
                continue
            }
            if (blockCommentDepth > 0) {
                if (char == '/' && next == '*') {
                    blockCommentDepth++
                    index += 2
                    continue
                }
                if (char == '*' && next == '/') {
                    blockCommentDepth--
                    index += 2
                    continue
                }
                index++
                continue
            }
            val delimiter = stringDelimiter
            if (delimiter != null) {
                if (escaped) {
                    escaped = false
                } else if (char == '\\') {
                    escaped = true
                } else if (char == delimiter) {
                    stringDelimiter = null
                }
                index++
                continue
            }

            if (char == '/' && next == '/') {
                lineComment = true
                index += 2
                continue
            }
            if (char == '/' && next == '*') {
                blockCommentDepth = 1
                index += 2
                continue
            }
            if (char == '"' || char == '\'') {
                stringDelimiter = char
                index++
                continue
            }

            if (char == open) {
                depth++
            } else if (char == close) {
                depth--
                if (depth == 0) {
                    return index
                }
            }
            index++
        }
        return null
    }

    private data class AttributeBlock(
        val names: List<String>,
        val startOffset: Int,
    )
}

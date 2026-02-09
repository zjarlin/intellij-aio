package site.addzero.i18n.buddy.keygen

/**
 * Key generator for ASCII-only strings. Simply converts to SCREAMING_SNAKE_CASE.
 *
 * "Hello World" → "HELLO_WORLD"
 * "confirm delete" → "CONFIRM_DELETE"
 * "loadingText" → "LOADING_TEXT"
 */
class AsciiKeyGenerator : KeyGenerator {

    override val displayName: String = "ASCII passthrough"

    override fun canHandle(text: String): Boolean = text.all { it.code < 128 }

    override fun generateKey(text: String): String {
        val result = StringBuilder()
        for ((i, ch) in text.withIndex()) {
            if (ch.isWhitespace() || ch in DELIMITERS) {
                if (result.isNotEmpty() && result.last() != '_') result.append('_')
                continue
            }
            if (ch.isUpperCase() && i > 0 && text[i - 1].isLowerCase()) {
                result.append('_')
            }
            if (ch.isLetterOrDigit() || ch == '_') {
                result.append(ch.uppercaseChar())
            }
        }
        var key = result.toString().replace(Regex("_+"), "_").trim('_')
        if (key.isNotEmpty() && key[0].isDigit()) key = "_$key"
        return key.ifEmpty { "UNNAMED" }
    }

    companion object {
        private val DELIMITERS = setOf('-', '.', ',', ':', ';', '!', '?', '(', ')', '[', ']', '{', '}', '/')
    }
}

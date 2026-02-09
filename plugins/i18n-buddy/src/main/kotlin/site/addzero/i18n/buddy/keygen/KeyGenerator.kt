package site.addzero.i18n.buddy.keygen

/**
 * SPI interface for generating constant key names from raw string literals.
 *
 * Default implementation: [PinyinKeyGenerator] (Chinese → PINYIN).
 * Users can provide custom implementations via ServiceLoader or plugin extensions.
 */
interface KeyGenerator {

    /** Human-readable name shown in settings dropdown. */
    val displayName: String

    /**
     * Generate a SCREAMING_SNAKE_CASE constant key from the given string.
     *
     * @param text the raw string literal content, e.g. "你好" or "Hello World"
     * @return a valid Kotlin/Java constant name, e.g. "NI_HAO" or "HELLO_WORLD"
     */
    fun generateKey(text: String): String

    /**
     * Whether this generator can handle the given text.
     * For example, a pinyin generator returns false for pure ASCII text.
     */
    fun canHandle(text: String): Boolean = true
}

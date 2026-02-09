package site.addzero.i18n.buddy.keygen

/**
 * Default key generator: converts Chinese characters to pinyin via PinYin4JUtils,
 * keeps ASCII as-is.
 *
 * "你好" → "NI_HAO"
 * "Hello World" → "HELLO_WORLD" (delegated to AsciiKeyGenerator)
 * "确认删除" → "QUE_REN_SHAN_CHU"
 * "混合test文本" → "HUN_HE_TEST_WEN_BEN"
 */
class PinyinKeyGenerator : KeyGenerator {

    override val displayName: String = "Pinyin (default)"

    private val asciiGen = AsciiKeyGenerator()

    override fun generateKey(text: String): String {
        // Pure ASCII — delegate to AsciiKeyGenerator
        if (isAsciiOnly(text)) {
            return asciiGen.generateKey(text)
        }
        // Chinese or mixed text — use PinYin4JUtils
        val pinyin = PinYin4JUtils.hanziToPinyin(text, "_")
        return PinYin4JUtils.sanitize(pinyin)
    }

    override fun canHandle(text: String): Boolean = true

    private fun isAsciiOnly(text: String): Boolean = text.all { it.code < 128 }
}

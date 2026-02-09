package site.addzero.i18n.buddy.keygen

import java.util.ServiceLoader

/**
 * Composite key generator that chains multiple strategies:
 *
 * 1. If text is pure ASCII → [AsciiKeyGenerator] (no translation needed)
 * 2. If an SPI [KeyGenerator] is available and can handle → use it (e.g. translation API)
 * 3. Fallback → [PinyinKeyGenerator]
 */
class CompositeKeyGenerator : KeyGenerator {

    override val displayName: String = "Auto (ASCII → SPI → Pinyin)"

    private val asciiGen = AsciiKeyGenerator()
    private val pinyinGen = PinyinKeyGenerator()
    private val spiGenerators: List<KeyGenerator> by lazy { loadSpiGenerators() }

    override fun generateKey(text: String): String {
        // 1. Pure ASCII — just convert case
        if (asciiGen.canHandle(text)) {
            return asciiGen.generateKey(text)
        }

        // 2. Try SPI providers (e.g. translation library)
        for (gen in spiGenerators) {
            if (gen.canHandle(text)) {
                return gen.generateKey(text)
            }
        }

        // 3. Fallback to pinyin
        return pinyinGen.generateKey(text)
    }

    private fun loadSpiGenerators(): List<KeyGenerator> {
        return try {
            ServiceLoader.load(KeyGenerator::class.java, javaClass.classLoader)
                .filter { it !is CompositeKeyGenerator && it !is AsciiKeyGenerator && it !is PinyinKeyGenerator }
                .toList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}

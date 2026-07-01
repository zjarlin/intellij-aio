package site.addzero.smart.intentions.find.sourceonly

internal object SourceOnlyFileNameSuffixFilter {
    fun parseText(text: String): List<String> {
        val suffixes = text
            .split('\n', ',', ';', '，', '；')
            .map { suffix -> suffix.trim() }
        return normalize(suffixes)
    }

    fun toText(suffixes: Collection<String>): String {
        return normalize(suffixes).joinToString("\n")
    }

    fun normalize(suffixes: Collection<String>): List<String> {
        return suffixes.asSequence()
            .map { suffix -> normalizeSuffix(suffix) }
            .filter { suffix -> suffix.isNotBlank() }
            .distinct()
            .toList()
    }

    fun matches(fileName: String, suffixes: Collection<String>): Boolean {
        val normalizedSuffixes = normalize(suffixes)
        if (normalizedSuffixes.isEmpty()) {
            return false
        }
        val nameWithoutExtension = fileName.substringBeforeLast('.', fileName)
        return normalizedSuffixes.any { suffix ->
            fileName.endsWith(suffix) || (!suffix.contains('.') && nameWithoutExtension.endsWith(suffix))
        }
    }

    private fun normalizeSuffix(suffix: String): String {
        return suffix
            .trim()
            .trimStart('*')
            .trimStart('/')
            .trimStart()
    }
}

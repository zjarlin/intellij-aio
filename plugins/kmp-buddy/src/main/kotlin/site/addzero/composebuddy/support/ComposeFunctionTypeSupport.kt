package site.addzero.composebuddy.support

object ComposeFunctionTypeSupport {
    private val annotationRegex = Regex("@[\\w.]+(?:\\([^)]*\\))?\\s*")
    private val whitespaceRegex = Regex("\\s+")

    fun extractReceiverTypeText(typeText: String): String? {
        val normalized = typeText
            .replace(annotationRegex, " ")
            .replace(whitespaceRegex, " ")
            .trim()
            .removeSuffix("?")
        val marker = ".() ->"
        val markerIndex = normalized.indexOf(marker)
        if (markerIndex < 0) {
            return null
        }
        return normalized
            .substring(0, markerIndex)
            .trimReceiverCandidate()
            .ifBlank { null }
    }

    private fun String.trimReceiverCandidate(): String {
        var candidate = trim()
        while (candidate.startsWith("(")) {
            candidate = candidate.removePrefix("(").trim()
        }
        while (candidate.endsWith(")")) {
            candidate = candidate.removeSuffix(")").trim()
        }
        return candidate
    }
}

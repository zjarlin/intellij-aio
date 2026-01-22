package site.addzero.idfixer

/**
 * Represents the result of a plugin ID replacement operation.
 */
data class ReplacementResult(
    val filesModified: Int,
    val replacementsMade: Int,
    val errors: List<String> = emptyList()
) {
    fun isSuccessful(): Boolean = errors.isEmpty()
    fun hasReplacements(): Boolean = replacementsMade > 0

    fun getSummaryMessage(): String {
        return when {
            !isSuccessful() -> "Replacement failed with ${errors.size} error(s)"
            !hasReplacements() -> "No plugin ID replacements were needed"
            filesModified == 1 -> "Successfully replaced $replacementsMade plugin ID(s) in 1 file"
            else -> "Successfully replaced $replacementsMade plugin ID(s) in $filesModified files"
        }
    }

    fun getDetailedMessage(): String {
        val summary = getSummaryMessage()
        return if (errors.isNotEmpty()) {
            "$summary:\n${errors.joinToString("\n")}"
        } else {
            summary
        }
    }

    companion object {
        fun noReplacements(): ReplacementResult = ReplacementResult(0, 0, emptyList())
        fun error(message: String): ReplacementResult = ReplacementResult(0, 0, listOf(message))
        fun success(filesModified: Int, replacementsMade: Int): ReplacementResult =
            ReplacementResult(filesModified, replacementsMade, emptyList())
    }
}

package site.addzero.idfixer

/**
 * Represents the result of a plugin ID replacement operation.
 *
 * This data class encapsulates statistics and error information from a bulk
 * or single plugin ID replacement operation. It provides a summary that can
 * be displayed to the user in notifications or dialogs.
 *
 * @property filesModified The number of files that were modified during the operation
 * @property replacementsMade The total number of plugin ID replacements performed
 * @property errors A list of error messages encountered during the operation (empty if no errors)
 *
 * @see ReplacementCandidate
 * @see IdReplacementEngine
 */
data class ReplacementResult(
    val filesModified: Int,
    val replacementsMade: Int,
    val errors: List<String> = emptyList()
) {
    /**
     * Checks if the operation was successful (no errors occurred).
     */
    fun isSuccessful(): Boolean = errors.isEmpty()

    /**
     * Checks if any replacements were made.
     */
    fun hasReplacements(): Boolean = replacementsMade > 0

    /**
     * Gets a human-readable summary message for display in notifications.
     */
    fun getSummaryMessage(): String {
        return when {
            !isSuccessful() -> "Replacement failed with ${errors.size} error(s)"
            !hasReplacements() -> "No plugin ID replacements were needed"
            filesModified == 1 -> "Successfully replaced $replacementsMade plugin ID(s) in 1 file"
            else -> "Successfully replaced $replacementsMade plugin ID(s) in $filesModified files"
        }
    }

    /**
     * Gets a detailed message including error information if present.
     */
    fun getDetailedMessage(): String {
        val summary = getSummaryMessage()
        return if (errors.isNotEmpty()) {
            "$summary:\n${errors.joinToString("\n")}"
        } else {
            summary
        }
    }

    companion object {
        /**
         * Creates a successful result with no replacements.
         */
        fun noReplacements(): ReplacementResult = ReplacementResult(0, 0, emptyList())

        /**
         * Creates a result with a single error.
         */
        fun error(message: String): ReplacementResult = ReplacementResult(0, 0, listOf(message))

        /**
         * Creates a successful result with the given statistics.
         */
        fun success(filesModified: Int, replacementsMade: Int): ReplacementResult =
            ReplacementResult(filesModified, replacementsMade, emptyList())
    }
}

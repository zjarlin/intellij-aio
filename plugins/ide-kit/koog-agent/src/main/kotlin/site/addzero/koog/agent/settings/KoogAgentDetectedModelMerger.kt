package site.addzero.koog.agent.settings

internal object KoogAgentDetectedModelMerger {
    fun merge(
        existingModels: Collection<KoogAgentModelState>,
        detectedModels: Collection<KoogAgentModelState>,
        addMissing: Boolean,
    ): MutableList<KoogAgentModelState> {
        val merged = existingModels.map { it.copy() }.toMutableList()

        detectedModels.forEach { detected ->
            val detectedFamily = detected.source.detectedSourceFamily()
            val firstMatchingIndex = merged.indexOfFirst { existing ->
                existing.detected && existing.source.detectedSourceFamily() == detectedFamily
            }

            if (firstMatchingIndex >= 0) {
                val existing = merged[firstMatchingIndex]
                val replacement = detected.copy()
                replacement.enabled = existing.enabled
                replacement.order = existing.order

                for (index in merged.lastIndex downTo 0) {
                    if (merged[index].detected && merged[index].source.detectedSourceFamily() == detectedFamily) {
                        merged.removeAt(index)
                    }
                }
                merged.add(firstMatchingIndex.coerceAtMost(merged.size), replacement)
            } else if (addMissing) {
                merged.add(detected.copy())
            }
        }

        return KoogAgentModelDeduplicator.deduplicate(merged)
    }

    private fun String.detectedSourceFamily(): String {
        val trimmed = trim()
        return when {
            trimmed.startsWith("Codex/", ignoreCase = true) -> "Codex"
            trimmed.startsWith("Claude/", ignoreCase = true) -> "Claude"
            trimmed.isBlank() -> ""
            else -> trimmed.substringBefore('/').trim()
        }
    }
}

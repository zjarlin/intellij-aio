package site.addzero.gradle.catalog

/**
 * 别名相似度匹配器
 * 用于在 TOML 中查找与给定引用最相似的别名
 */
class AliasSimilarityMatcher {

    /**
     * 匹配结果
     */
    data class MatchResult(
        val alias: String,
        val score: Double,
        val matchedTokens: List<String>
    ) : Comparable<MatchResult> {
        override fun compareTo(other: MatchResult): Int {
            return other.score.compareTo(this.score) // 降序排列
        }
    }

    /**
     * 查找最相似的别名
     * @param invalidReference 无效的引用（如 com.google.devtools.ksp.gradle.plugin）
     * @param availableAliases 可用的别名集合
     * @return 匹配结果列表，按相似度降序排列（包含所有有 token 匹配的候选项）
     */
    fun findSimilarAliases(
        invalidReference: String,
        availableAliases: Set<String>
    ): List<MatchResult> {
        // 剥离 Gradle 版本后缀后再分词，同时保留原始 tokens 用于回退
        val strippedReference = stripGradleVersionSuffix(invalidReference)
        val referenceTokens = tokenize(strippedReference)

        println("[AliasSimilarityMatcher] Reference tokens (stripped='$strippedReference'): $referenceTokens")

        // 计算每个别名的相似度
        val results = availableAliases.map { alias ->
            val aliasTokens = tokenize(alias)
            val score = calculateSimilarity(referenceTokens, aliasTokens)
            val matchedTokens = findMatchedTokens(referenceTokens, aliasTokens)

            MatchResult(alias, score, matchedTokens)
        }.filter { it.score > 0.0 } // 只保留有匹配的结果（即至少有一个 token 匹配）
            .sorted() // 按分数降序排列

        println("[AliasSimilarityMatcher] Found ${results.size} matches with at least one token:")
        results.take(20).forEach { println("  - ${it.alias} (score: ${it.score}, matched: ${it.matchedTokens})") }
        if (results.size > 20) {
            println("  ... and ${results.size - 20} more")
        }

        return results
    }

    /**
     * 分词：将引用拆分为单词列表
     * 例如：com.google.devtools.ksp.gradle.plugin -> [com, google, devtools, ksp, gradle, plugin]
     */
    private fun tokenize(reference: String): List<String> {
        return reference
            .split('.', '-', '_')
            .map { it.lowercase() }
            .filter { it.isNotEmpty() }
    }

    companion object {
        /**
         * Gradle 版本后缀正则：匹配末尾的 .vN、.vNaltN、.vNrcN、.vNbetaN、.vNalphaN 等
         *
         * Gradle 在生成 version catalog accessor 时，如果 TOML alias 的 segment 以数字开头
         * 或者存在版本歧义，会追加版本后缀。例如：
         *   io-ktor-ktor-client-core + version.ref="ktor"(3.0.0-beta-2)
         *   → libs.io.ktor.ktor.client.core.v3alt2
         *
         * 常见后缀模式：
         *   .v0, .v2026, .v3alt2, .v2rc1, .v1beta3, .v4alpha1
         */
        private val GRADLE_VERSION_SUFFIX = Regex(
            """(?:\.[vV]\d+(?:alt|rc|beta|alpha|snapshot|dev|pre|m|cr)?\d*)+$"""
        )

        /**
         * 剥离 Gradle 生成的版本后缀
         * 例如：
         *   io.ktor.ktor.client.core.v3alt2 → io.ktor.ktor.client.core
         *   org.babyfish.jimmer.jimmer.ksp.v0 → org.babyfish.jimmer.jimmer.ksp
         *   site.addzero.ioc.core.v2026 → site.addzero.ioc.core
         *   androidx.compose.bom → androidx.compose.bom（无后缀，不变）
         */
        fun stripGradleVersionSuffix(reference: String): String {
            return GRADLE_VERSION_SUFFIX.replace(reference, "")
        }
    }

    /**
     * 计算相似度分数
     * 使用多种策略：
     * 1. 完全匹配的 token 数量
     * 2. 部分匹配的 token 数量
     * 3. token 顺序的相似度
     * 4. Jaccard 相似度
     */
    private fun calculateSimilarity(referenceTokens: List<String>, aliasTokens: List<String>): Double {
        if (referenceTokens.isEmpty() || aliasTokens.isEmpty()) {
            return 0.0
        }

        // 1. 完全匹配的 token 数量（权重最高）
        val exactMatches = referenceTokens.count { it in aliasTokens }
        val exactMatchScore = exactMatches.toDouble() / referenceTokens.size * 0.5

        // 2. Jaccard 相似度（集合相似度）
        val intersection = referenceTokens.toSet().intersect(aliasTokens.toSet()).size
        val union = referenceTokens.toSet().union(aliasTokens.toSet()).size
        val jaccardScore = if (union > 0) intersection.toDouble() / union * 0.3 else 0.0

        // 3. 顺序相似度（考虑 token 的顺序）
        val orderScore = calculateOrderSimilarity(referenceTokens, aliasTokens) * 0.2

        return exactMatchScore + jaccardScore + orderScore
    }

    /**
     * 计算顺序相似度
     * 如果 token 在两个列表中的相对顺序相同，得分更高
     */
    private fun calculateOrderSimilarity(referenceTokens: List<String>, aliasTokens: List<String>): Double {
        val commonTokens = referenceTokens.filter { it in aliasTokens }
        if (commonTokens.isEmpty()) {
            return 0.0
        }

        var orderMatches = 0
        for (i in 0 until commonTokens.size - 1) {
            val token1 = commonTokens[i]
            val token2 = commonTokens[i + 1]

            val refIndex1 = referenceTokens.indexOf(token1)
            val refIndex2 = referenceTokens.indexOf(token2)
            val aliasIndex1 = aliasTokens.indexOf(token1)
            val aliasIndex2 = aliasTokens.indexOf(token2)

            // 如果两个 token 在两个列表中的相对顺序相同
            if ((refIndex1 < refIndex2 && aliasIndex1 < aliasIndex2) ||
                (refIndex1 > refIndex2 && aliasIndex1 > aliasIndex2)) {
                orderMatches++
            }
        }

        return if (commonTokens.size > 1) {
            orderMatches.toDouble() / (commonTokens.size - 1)
        } else {
            0.0
        }
    }

    /**
     * 找到匹配的 token
     */
    private fun findMatchedTokens(referenceTokens: List<String>, aliasTokens: List<String>): List<String> {
        return referenceTokens.filter { it in aliasTokens }
    }
}

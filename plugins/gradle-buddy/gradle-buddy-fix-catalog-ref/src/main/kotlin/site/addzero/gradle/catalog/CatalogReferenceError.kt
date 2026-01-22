package site.addzero.gradle.catalog

/**
 * 版本目录引用错误类型
 */
sealed class CatalogReferenceError {
    abstract val catalogName: String
    abstract val invalidReference: String

    /**
     * 引用格式错误（TOML 中有声明，但引用格式不对）
     * 例如: TOML 中声明了 gradle-plugin-ksp，但代码中写成了 gradlePlugin.ksp
     */
    data class WrongFormat(
        override val catalogName: String,
        override val invalidReference: String,
        val correctReference: String,
        val availableAliases: Set<String>
    ) : CatalogReferenceError()

    /**
     * 依赖未声明（TOML 中根本没有这个声明）
     * 例如: 代码中使用了 libs.some.library，但 TOML 中没有对应的声明
     * @param suggestedAliases 建议的相似别名列表（按相似度排序）
     */
    data class NotDeclared(
        override val catalogName: String,
        override val invalidReference: String,
        val availableAliases: Set<String>,
        val suggestedAliases: List<AliasSimilarityMatcher.MatchResult> = emptyList()
    ) : CatalogReferenceError()
}

package site.addzero.smart.intentions.kotlin.inheritbase

internal data class BaseTypeChoice(
    val displayName: String,
    val typeText: String,
    val qualifiedName: String?,
    val packageName: String?,
    val typeParameterNames: List<String>,
) {
    override fun toString(): String {
        if (packageName.isNullOrBlank()) {
            return displayName
        }
        return "$displayName  ($packageName)"
    }
}

internal data class AddedSuperType(
    val superTypeText: String,
    val typeArgumentRanges: List<IntRange>,
)

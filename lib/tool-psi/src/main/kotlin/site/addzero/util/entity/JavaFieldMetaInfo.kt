package site.addzero.util.entity
data class JavaFieldMetaInfo(
    val name: String,
    val type: Class<*>,
    val genericType: Class<*>,
    val comment: String,
)

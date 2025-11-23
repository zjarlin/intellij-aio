package site.addzero.addl.autoddlstarter.generator.entity
@Deprecated("请使用Lsi语言抽象替换")
data class JavaFieldMetaInfo(
    val name: String,
    val type: Class<*>,
    val genericType: Class<*>,
    val comment: String,
)

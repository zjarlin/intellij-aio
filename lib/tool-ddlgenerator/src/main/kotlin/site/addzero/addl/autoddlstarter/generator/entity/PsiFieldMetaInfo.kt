package site.addzero.addl.autoddlstarter.generator.entity
@Deprecated("请使用Lsi语言抽象替换")
data class PsiFieldMetaInfo(
    val pkg: String?,
    val classname: String?,
    val classcomment: String?,
    val javaFieldMetaInfos: List<JavaFieldMetaInfo> ?
)

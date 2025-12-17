package site.addzero.addl.autoddlstarter.generator.entity

/**
 * PSI 字段元信息（兼容旧版本）
 *
 * @param pkg 包名
 * @param classname 类名
 * @param classcomment 类注释
 * @param javaFieldMetaInfos Java字段元信息列表
 */
data class PsiFieldMetaInfo(
    val pkg: String?,
    val classname: String?,
    val classcomment: String?,
    val javaFieldMetaInfos: List<JavaFieldMetaInfo>
)

/**
 * Java字段元信息
 */
data class JavaFieldMetaInfo(
    val fieldName: String,
    val fieldType: String,
    val isNullable: Boolean,
    val fieldComment: String?,
    val defaultValue: String?
)
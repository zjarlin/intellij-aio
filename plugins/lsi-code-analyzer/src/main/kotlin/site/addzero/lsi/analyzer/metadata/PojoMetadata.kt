package site.addzero.lsi.analyzer.metadata

import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.clazz.guessTableName

data class PojoMetadata(
    val qualifiedName: String,
    val className: String,
    val packageName: String,
    val comment: String?,
    val tableName: String?,
    val isInterface: Boolean,
    val isEnum: Boolean,
    val fields: List<FieldMetadata>,
    val annotations: List<AnnotationMetadata>,
    val scanTime: Long = System.currentTimeMillis()
) {
    val dbFields: List<FieldMetadata>
        get() = fields.filter { !it.isStatic && !it.isCollectionType && !it.isTransient }

    companion object {
        fun from(lsiClass: LsiClass): PojoMetadata {
            val qualifiedName = lsiClass.qualifiedName ?: ""
            val packageName = qualifiedName.substringBeforeLast('.', "")

            return PojoMetadata(
                qualifiedName = qualifiedName,
                className = lsiClass.name ?: "",
                packageName = packageName,
                comment = lsiClass.comment,
                tableName = lsiClass.guessTableName.takeIf { it.isNotBlank() },
                isInterface = lsiClass.isInterface,
                isEnum = lsiClass.isEnum,
                fields = lsiClass.fields.map { FieldMetadata.from(it) },
                annotations = lsiClass.annotations.map { AnnotationMetadata.from(it) }
            )
        }
    }
}

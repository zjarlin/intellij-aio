package site.addzero.lsi.analyzer.metadata

import site.addzero.util.lsi.field.LsiField

data class FieldMetadata(
    val name: String,
    val typeName: String,
    val typeQualifiedName: String?,
    val comment: String?,
    val columnName: String?,
    val nullable: Boolean,
    val isPrimaryKey: Boolean,
    val isTransient: Boolean,
    val isStatic: Boolean,
    val isCollectionType: Boolean,
    val defaultValue: String?,
    val annotations: List<AnnotationMetadata>
) {
    companion object {
        fun from(lsiField: LsiField): FieldMetadata {
            val isPrimaryKey = lsiField.annotations.any {
                it.qualifiedName?.endsWith(".Id") == true
            } || lsiField.name?.equals("id", ignoreCase = true) == true

            val isTransient = lsiField.annotations.any {
                it.qualifiedName?.endsWith(".Transient") == true
            }

            val nullable = lsiField.type?.isNullable ?: true

            return FieldMetadata(
                name = lsiField.name ?: "",
                typeName = lsiField.typeName ?: "",
                typeQualifiedName = lsiField.type?.qualifiedName,
                comment = lsiField.comment,
                columnName = lsiField.columnName,
                nullable = nullable,
                isPrimaryKey = isPrimaryKey,
                isTransient = isTransient,
                isStatic = lsiField.isStatic,
                isCollectionType = lsiField.isCollectionType,
                defaultValue = lsiField.defaultValue,
                annotations = lsiField.annotations.map { AnnotationMetadata.from(it) }
            )
        }
    }
}

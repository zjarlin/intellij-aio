package site.addzero.lsi.analyzer.jimmer

object JimmerAnnotations {
    private const val JIMMER_SQL_PKG = "org.babyfish.jimmer.sql"

    const val ENTITY = "$JIMMER_SQL_PKG.Entity"
    const val MAPPED_SUPERCLASS = "$JIMMER_SQL_PKG.MappedSuperclass"
    const val TABLE = "$JIMMER_SQL_PKG.Table"
    const val COLUMN = "$JIMMER_SQL_PKG.Column"
    const val ID = "$JIMMER_SQL_PKG.Id"
    const val GENERATED_VALUE = "$JIMMER_SQL_PKG.GeneratedValue"
    const val KEY = "$JIMMER_SQL_PKG.Key"
    const val TRANSIENT = "$JIMMER_SQL_PKG.Transient"
    const val NULLABLE = "org.jetbrains.annotations.Nullable"
    const val DEFAULT = "$JIMMER_SQL_PKG.Default"

    const val ONE_TO_ONE = "$JIMMER_SQL_PKG.OneToOne"
    const val ONE_TO_MANY = "$JIMMER_SQL_PKG.OneToMany"
    const val MANY_TO_ONE = "$JIMMER_SQL_PKG.ManyToOne"
    const val MANY_TO_MANY = "$JIMMER_SQL_PKG.ManyToMany"
    const val JOIN_TABLE = "$JIMMER_SQL_PKG.JoinTable"
    const val JOIN_COLUMN = "$JIMMER_SQL_PKG.JoinColumn"

    const val FORMULA = "$JIMMER_SQL_PKG.Formula"
    const val SERIALIZED = "$JIMMER_SQL_PKG.Serialized"
    const val LOGICAL_DELETED = "$JIMMER_SQL_PKG.LogicalDeleted"
    const val VERSION = "$JIMMER_SQL_PKG.Version"

    val ENTITY_ANNOTATIONS = setOf(ENTITY, MAPPED_SUPERCLASS)

    val ASSOCIATION_ANNOTATIONS = setOf(ONE_TO_ONE, ONE_TO_MANY, MANY_TO_ONE, MANY_TO_MANY)

    fun isJimmerEntity(annotationQualifiedNames: List<String>): Boolean =
        annotationQualifiedNames.any { it in ENTITY_ANNOTATIONS }

    fun isAssociation(annotationQualifiedName: String?): Boolean =
        annotationQualifiedName in ASSOCIATION_ANNOTATIONS
}

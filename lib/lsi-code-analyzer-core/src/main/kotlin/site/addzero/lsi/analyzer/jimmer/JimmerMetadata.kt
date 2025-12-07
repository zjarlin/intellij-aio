package site.addzero.lsi.analyzer.jimmer

import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.field.LsiField

enum class IdGenerationStrategy {
    AUTO, IDENTITY, SEQUENCE, USER
}

enum class AssociationType {
    ONE_TO_ONE, ONE_TO_MANY, MANY_TO_ONE, MANY_TO_MANY
}

data class JimmerTableInfo(
    val name: String?,
    val catalog: String?,
    val schema: String?
)

data class JimmerKeyInfo(
    val properties: List<String>
)

data class JimmerAssociation(
    val fieldName: String,
    val type: AssociationType,
    val targetEntity: String?,
    val mappedBy: String?,
    val joinTableName: String?,
    val joinColumnName: String?,
    val inverseJoinColumnName: String?
)

data class JimmerLsiField(
    val base: LsiField,
    val columnInfo: JimmerColumnInfo?,
    val association: JimmerAssociation?,
    val isFormula: Boolean,
    val isSerialized: Boolean,
    val isLogicalDeleted: Boolean,
    val isVersion: Boolean,
    val defaultExpression: String?
)

data class JimmerColumnInfo(
    val name: String?,
    val sqlType: String?
)

data class JimmerEntityMetadata(
    val base: LsiClass,
    val tableInfo: JimmerTableInfo,
    val associations: List<JimmerAssociation>,
    val keys: List<JimmerKeyInfo>,
    val idStrategy: IdGenerationStrategy?,
    val jimmerFields: List<JimmerLsiField>
) {
    companion object {
        fun from(lsiClass: LsiClass): JimmerEntityMetadata {
            val tableInfo = extractTableInfo(lsiClass)
            val associations = extractAssociations(lsiClass)
            val keys = extractKeys(lsiClass)
            val idStrategy = extractIdStrategy(lsiClass)
            val jimmerFields = extractJimmerFields(lsiClass, lsiClass.fields)

            return JimmerEntityMetadata(
                base = lsiClass,
                tableInfo = tableInfo,
                associations = associations,
                keys = keys,
                idStrategy = idStrategy,
                jimmerFields = jimmerFields
            )
        }

        private fun extractTableInfo(lsiClass: LsiClass): JimmerTableInfo {
            val tableAnno = lsiClass.annotations.find {
                it.qualifiedName == JimmerAnnotations.TABLE
            }
            return JimmerTableInfo(
                name = tableAnno?.getAttribute("name")?.toString()?.trim('"'),
                catalog = tableAnno?.getAttribute("catalog")?.toString()?.trim('"'),
                schema = tableAnno?.getAttribute("schema")?.toString()?.trim('"')
            )
        }

        private fun extractAssociations(lsiClass: LsiClass): List<JimmerAssociation> =
            lsiClass.fields.mapNotNull { field ->
                val assocAnno = field.annotations.find {
                    JimmerAnnotations.isAssociation(it.qualifiedName)
                } ?: return@mapNotNull null

                val type = when (assocAnno.qualifiedName) {
                    JimmerAnnotations.ONE_TO_ONE -> AssociationType.ONE_TO_ONE
                    JimmerAnnotations.ONE_TO_MANY -> AssociationType.ONE_TO_MANY
                    JimmerAnnotations.MANY_TO_ONE -> AssociationType.MANY_TO_ONE
                    JimmerAnnotations.MANY_TO_MANY -> AssociationType.MANY_TO_MANY
                    else -> return@mapNotNull null
                }

                val joinTableAnno = field.annotations.find {
                    it.qualifiedName == JimmerAnnotations.JOIN_TABLE
                }
                val joinColumnAnno = field.annotations.find {
                    it.qualifiedName == JimmerAnnotations.JOIN_COLUMN
                }

                JimmerAssociation(
                    fieldName = field.name ?: "",
                    type = type,
                    targetEntity = field.type?.qualifiedName,
                    mappedBy = assocAnno.getAttribute("mappedBy")?.toString()?.trim('"'),
                    joinTableName = joinTableAnno?.getAttribute("name")?.toString()?.trim('"'),
                    joinColumnName = joinColumnAnno?.getAttribute("name")?.toString()?.trim('"'),
                    inverseJoinColumnName = joinTableAnno?.getAttribute("inverseJoinColumnName")?.toString()?.trim('"')
                )
            }

        private fun extractKeys(lsiClass: LsiClass): List<JimmerKeyInfo> {
            val keyAnnos = lsiClass.fields.filter { field ->
                field.annotations.any { it.qualifiedName == JimmerAnnotations.KEY }
            }
            return if (keyAnnos.isNotEmpty()) {
                listOf(JimmerKeyInfo(keyAnnos.mapNotNull { it.name }))
            } else {
                emptyList()
            }
        }

        private fun extractIdStrategy(lsiClass: LsiClass): IdGenerationStrategy? {
            val idField = lsiClass.fields.find { field ->
                field.annotations.any { it.qualifiedName == JimmerAnnotations.ID }
            } ?: return null

            val genValueAnno = idField.annotations.find {
                it.qualifiedName == JimmerAnnotations.GENERATED_VALUE
            } ?: return IdGenerationStrategy.USER

            val strategy = genValueAnno.getAttribute("strategy")?.toString() ?: return IdGenerationStrategy.AUTO
            return when {
                strategy.contains("IDENTITY") -> IdGenerationStrategy.IDENTITY
                strategy.contains("SEQUENCE") -> IdGenerationStrategy.SEQUENCE
                strategy.contains("AUTO") -> IdGenerationStrategy.AUTO
                else -> IdGenerationStrategy.USER
            }
        }

        private fun extractJimmerFields(lsiClass: LsiClass, baseFields: List<LsiField>): List<JimmerLsiField> =
            lsiClass.fields.mapIndexed { index, lsiField ->
                val baseField = baseFields.getOrElse(index) { lsiField }
                val columnAnno = lsiField.annotations.find {
                    it.qualifiedName == JimmerAnnotations.COLUMN
                }
                val columnInfo = columnAnno?.let {
                    JimmerColumnInfo(
                        name = it.getAttribute("name")?.toString()?.trim('"'),
                        sqlType = it.getAttribute("sqlType")?.toString()?.trim('"')
                    )
                }

                val association = lsiField.annotations.find {
                    JimmerAnnotations.isAssociation(it.qualifiedName)
                }?.let { assocAnno ->
                    val type = when (assocAnno.qualifiedName) {
                        JimmerAnnotations.ONE_TO_ONE -> AssociationType.ONE_TO_ONE
                        JimmerAnnotations.ONE_TO_MANY -> AssociationType.ONE_TO_MANY
                        JimmerAnnotations.MANY_TO_ONE -> AssociationType.MANY_TO_ONE
                        JimmerAnnotations.MANY_TO_MANY -> AssociationType.MANY_TO_MANY
                        else -> null
                    }
                    type?.let {
                        JimmerAssociation(
                            fieldName = lsiField.name ?: "",
                            type = it,
                            targetEntity = lsiField.type?.qualifiedName,
                            mappedBy = assocAnno.getAttribute("mappedBy")?.toString()?.trim('"'),
                            joinTableName = null,
                            joinColumnName = null,
                            inverseJoinColumnName = null
                        )
                    }
                }

                val defaultAnno = lsiField.annotations.find {
                    it.qualifiedName == JimmerAnnotations.DEFAULT
                }

                JimmerLsiField(
                    base = baseField,
                    columnInfo = columnInfo,
                    association = association,
                    isFormula = lsiField.annotations.any { it.qualifiedName == JimmerAnnotations.FORMULA },
                    isSerialized = lsiField.annotations.any { it.qualifiedName == JimmerAnnotations.SERIALIZED },
                    isLogicalDeleted = lsiField.annotations.any { it.qualifiedName == JimmerAnnotations.LOGICAL_DELETED },
                    isVersion = lsiField.annotations.any { it.qualifiedName == JimmerAnnotations.VERSION },
                    defaultExpression = defaultAnno?.getAttribute("value")?.toString()?.trim('"')
                )
            }
    }
}

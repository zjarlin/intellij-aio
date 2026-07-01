package site.addzero.smart.intentions.kotlin.entityfieldmerge

internal object EntityFieldMergePlanner {
    fun createPlan(
        target: EntityFieldClassModel,
        sources: List<EntityFieldClassModel>,
    ): EntityFieldMergePlan? {
        val targetBySignature = target.fields.associateBy { field -> field.signatureKey }
        val targetByName = target.fields.associateBy { field -> field.name }
        val mergedSourceFields = sources
            .flatMap { source -> source.fields }
            .distinctBy { field -> field.signatureKey }

        val rows = mergedSourceFields.map { sourceField ->
            val sameTargetField = targetBySignature[sourceField.signatureKey]
            val nameMatchedTargetField = if (sameTargetField == null) {
                targetByName[sourceField.name]
            } else {
                null
            }
            EntityFieldMergeRow(
                sourceField = sourceField,
                sameTargetField = sameTargetField,
                nameMatchedTargetField = nameMatchedTargetField,
            )
        }

        if (rows.isEmpty()) {
            return null
        }
        return EntityFieldMergePlan(
            target = target,
            sources = sources,
            rows = rows,
        )
    }
}

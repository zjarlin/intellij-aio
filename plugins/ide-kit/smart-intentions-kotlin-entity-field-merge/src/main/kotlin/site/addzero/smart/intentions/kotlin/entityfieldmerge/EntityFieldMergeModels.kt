package site.addzero.smart.intentions.kotlin.entityfieldmerge

import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty

internal data class EntityFieldMergePlan(
    val target: EntityFieldClassModel,
    val sources: List<EntityFieldClassModel>,
    val rows: List<EntityFieldMergeRow>,
) {
    val importFqNames: List<String> = rows
        .flatMap { row -> row.sourceField.importFqNames }
        .distinct()
        .sorted()
}

internal data class EntityFieldClassModel(
    val klass: KtClass,
    val fields: List<EntityFieldModel>,
) {
    val displayName: String = klass.name.orEmpty()
}

internal data class EntityFieldModel(
    val name: String,
    val typeText: String,
    val declarationText: String,
    val importFqNames: List<String>,
    val sourceClassName: String,
    val origin: EntityFieldOrigin,
    val property: KtProperty? = null,
    val parameter: KtParameter? = null,
) {
    val signatureKey: EntityFieldSignatureKey = EntityFieldSignatureKey(name, typeText)
}

internal enum class EntityFieldOrigin {
    PRIMARY_CONSTRUCTOR,
    BODY_PROPERTY,
}

internal data class EntityFieldSignatureKey(
    val name: String,
    val typeText: String,
)

internal data class EntityFieldMergeRow(
    val sourceField: EntityFieldModel,
    val sameTargetField: EntityFieldModel?,
    val nameMatchedTargetField: EntityFieldModel?,
) {
    val defaultSelected: Boolean = sameTargetField == null && nameMatchedTargetField == null

    val status: EntityFieldMergeStatus = when {
        sameTargetField != null -> EntityFieldMergeStatus.SAME
        nameMatchedTargetField != null -> EntityFieldMergeStatus.CONFLICT
        else -> EntityFieldMergeStatus.NEW
    }
}

internal enum class EntityFieldMergeStatus {
    NEW,
    SAME,
    CONFLICT,
}

internal data class EntityFieldMergeSelection(
    val selectedRows: List<EntityFieldMergeRow>,
    val deleteSourceFiles: Boolean,
)

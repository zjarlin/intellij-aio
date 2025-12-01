package site.addzero.lsi.analyzer.metadata

import site.addzero.util.lsi.anno.LsiAnnotation

data class AnnotationMetadata(
    val qualifiedName: String,
    val simpleName: String,
    val attributes: Map<String, String>
) {
    companion object {
        fun from(lsiAnnotation: LsiAnnotation): AnnotationMetadata = AnnotationMetadata(
            qualifiedName = lsiAnnotation.qualifiedName ?: "",
            simpleName = lsiAnnotation.simpleName ?: "",
            attributes = lsiAnnotation.attributes.mapValues { it.value?.toString() ?: "" }
        )
    }
}

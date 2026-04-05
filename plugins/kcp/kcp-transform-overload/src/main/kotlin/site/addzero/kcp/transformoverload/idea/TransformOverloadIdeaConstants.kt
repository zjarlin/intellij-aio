package site.addzero.kcp.transformoverload.idea

internal object TransformOverloadIdeaConstants {
    const val ideaPluginId = "site.addzero.kcp-transform-overload-idea-plugin"

    const val generateTransformOverloadsAnnotation =
        "site.addzero.kcp.transformoverload.annotations.GenerateTransformOverloads"
    const val overloadTransformAnnotation =
        "site.addzero.kcp.transformoverload.annotations.OverloadTransform"
    const val transformProvider =
        "site.addzero.kcp.transformoverload.annotations.TransformProvider"

    const val stubFileName = "TransformOverloadStubs.kt"
    const val stubErrorMessage = "Transform overload IDE stub only"

    val liftKindsByClassId: Map<String, LiftKind> = mapOf(
        "kotlin.collections.Iterable" to LiftKind.ITERABLE,
        "kotlin.collections.Collection" to LiftKind.COLLECTION,
        "kotlin.collections.List" to LiftKind.LIST,
        "kotlin.collections.Set" to LiftKind.SET,
        "kotlin.sequences.Sequence" to LiftKind.SEQUENCE,
    )
}

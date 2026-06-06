package site.addzero.composebuddy.previewsandbox

object PreviewSandboxExternalDependencies {
    private const val ANDROIDX_LIFECYCLE_VERSION = "2.10.0"
    private const val KYANT_BACKDROP_VERSION = "2.0.0-alpha03"
    private const val KYANT_SHAPES_VERSION = "1.2.0"
    private const val KOIN_VERSION = "4.2.1"
    private const val KOTLINX_COROUTINES_VERSION = "1.11.0"
    private const val KOTLINX_SERIALIZATION_VERSION = "1.11.0"

    const val KYANT_BACKDROP: String = "io.github.kyant0:backdrop:$KYANT_BACKDROP_VERSION"
    const val KYANT_SHAPES: String = "io.github.kyant0:shapes:$KYANT_SHAPES_VERSION"
    const val KOIN_ANNOTATIONS: String = "io.insert-koin:koin-annotations:$KOIN_VERSION"
    const val KOIN_COMPOSE: String = "io.insert-koin:koin-compose:$KOIN_VERSION"
    const val KOIN_CORE: String = "io.insert-koin:koin-core:$KOIN_VERSION"

    const val KOTLINX_SERIALIZATION_CORE: String =
        "org.jetbrains.kotlinx:kotlinx-serialization-core:$KOTLINX_SERIALIZATION_VERSION"

    fun infer(files: List<PreviewSandboxSourceFile>): List<String> {
        val text = files.joinToString("\n") { file ->
            buildString {
                file.imports.forEach(::appendLine)
                file.declarations.forEach(::appendLine)
            }
        }
        return buildList {
            if (text.contains("androidx.lifecycle.")) {
                add("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:$ANDROIDX_LIFECYCLE_VERSION")
            }
            if (text.contains("com.kyant.backdrop.")) {
                add(KYANT_BACKDROP)
            }
            if (text.contains("com.kyant.shapes.")) {
                add(KYANT_SHAPES)
            }
            if (text.contains("org.koin.compose.")) {
                add(KOIN_COMPOSE)
                add(KOIN_CORE)
            }
            if (text.contains("org.koin.core.annotation.")) {
                add(KOIN_ANNOTATIONS)
            }
            if (text.contains("org.koin.core.")) {
                add(KOIN_CORE)
            }
            if (text.contains("kotlinx.coroutines.")) {
                add("org.jetbrains.kotlinx:kotlinx-coroutines-core:$KOTLINX_COROUTINES_VERSION")
            }
            if (text.contains("kotlinx.serialization.")) {
                add(KOTLINX_SERIALIZATION_CORE)
            }
        }.distinct().sorted()
    }
}

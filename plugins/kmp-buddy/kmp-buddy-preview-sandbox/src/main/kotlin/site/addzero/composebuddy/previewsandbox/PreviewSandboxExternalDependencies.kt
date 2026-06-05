package site.addzero.composebuddy.previewsandbox

object PreviewSandboxExternalDependencies {
    private const val ANDROIDX_LIFECYCLE_VERSION = "2.10.0"
    private const val KOIN_VERSION = "4.2.1"
    private const val KOTLINX_COROUTINES_VERSION = "1.11.0"
    private const val KOTLINX_SERIALIZATION_VERSION = "1.11.0"

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
            if (text.contains("org.koin.compose.")) {
                add("io.insert-koin:koin-compose:$KOIN_VERSION")
            }
            if (text.contains("org.koin.core.annotation.")) {
                add("io.insert-koin:koin-annotations:$KOIN_VERSION")
            }
            if (text.contains("org.koin.core.")) {
                add("io.insert-koin:koin-core:$KOIN_VERSION")
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

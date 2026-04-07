package site.addzero.gradle.sleep

internal fun resolveProjectViewFocusSeedModules(
    manualInputActive: Boolean,
    manualModules: Set<String>,
    loadedModules: Set<String>,
    selectedModules: Set<String>
): Set<String> {
    val sourceModules = if (manualInputActive) {
        manualModules + selectedModules
    } else {
        loadedModules + selectedModules
    }

    return sourceModules
        .filterNot { it.isBlank() || it == ":" }
        .toSet()
}

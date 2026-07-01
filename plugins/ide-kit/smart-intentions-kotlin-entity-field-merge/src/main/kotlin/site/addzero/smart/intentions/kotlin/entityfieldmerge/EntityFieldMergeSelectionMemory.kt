package site.addzero.smart.intentions.kotlin.entityfieldmerge

internal object EntityFieldMergeSelectionMemory {
    private val selectedSourceFiles = linkedSetOf<String>()

    fun remember(files: Collection<String>) {
        selectedSourceFiles.clear()
        selectedSourceFiles.addAll(files.filter { path -> path.isNotBlank() })
    }

    fun clear() {
        selectedSourceFiles.clear()
    }

    fun snapshot(): Set<String> {
        return selectedSourceFiles.toSet()
    }
}

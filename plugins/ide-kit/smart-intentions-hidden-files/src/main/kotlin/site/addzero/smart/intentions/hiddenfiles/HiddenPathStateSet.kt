package site.addzero.smart.intentions.hiddenfiles

import com.intellij.openapi.util.io.FileUtil

internal object HiddenPathStateSet {
    fun sanitize(state: HiddenFilesState): HiddenFilesState {
        val sanitized = HiddenFilesState()
        sanitized.showHiddenFiles = state.showHiddenFiles

        state.hiddenPaths
            .asSequence()
            .mapNotNull { entry ->
                val normalizedPath = normalize(entry.path)
                if (normalizedPath.isBlank()) {
                    null
                } else {
                    HiddenPathState(normalizedPath, entry.directory)
                }
            }
            .sortedBy { it.path.length }
            .forEach { add(sanitized.hiddenPaths, it.path, it.directory) }

        return sanitized
    }

    fun add(entries: MutableList<HiddenPathState>, path: String, directory: Boolean): Boolean {
        val normalizedPath = normalize(path)
        if (normalizedPath.isBlank()) {
            return false
        }
        if (entries.any { matches(it, normalizedPath) }) {
            return false
        }

        entries.removeAll { entry ->
            FileUtil.pathsEqual(entry.path, normalizedPath) ||
                (directory && FileUtil.isAncestor(normalizedPath, entry.path, false))
        }
        entries.add(HiddenPathState(normalizedPath, directory))
        return true
    }

    fun removeAffecting(entries: MutableList<HiddenPathState>, path: String): Boolean {
        val normalizedPath = normalize(path)
        if (normalizedPath.isBlank()) {
            return false
        }
        return entries.removeAll { matches(it, normalizedPath) }
    }

    fun contains(entries: Collection<HiddenPathState>, path: String): Boolean {
        val normalizedPath = normalize(path)
        if (normalizedPath.isBlank()) {
            return false
        }
        return entries.any { matches(it, normalizedPath) }
    }

    fun normalize(path: String): String {
        val normalized = FileUtil.toSystemIndependentName(path.trim())
        return if (normalized.length > 1) normalized.trimEnd('/') else normalized
    }

    private fun matches(entry: HiddenPathState, candidatePath: String): Boolean {
        return FileUtil.pathsEqual(entry.path, candidatePath) ||
            (entry.directory && FileUtil.isAncestor(entry.path, candidatePath, false))
    }
}

package site.addzero.smart.intentions.modulelock

import com.intellij.openapi.util.io.FileUtil

internal object ModuleLockStateSet {
    fun sanitize(state: ModuleLockState): ModuleLockState {
        val sanitized = ModuleLockState()
        sanitized.showLockedModules = state.showLockedModules

        state.lockedModules
            .asSequence()
            .mapNotNull { entry ->
                val moduleName = entry.moduleName.trim()
                val rootPaths = entry.rootPaths
                    .asSequence()
                    .map(::normalize)
                    .filter { path -> path.isNotBlank() }
                    .distinct()
                    .sorted()
                    .toList()
                if (moduleName.isBlank() || rootPaths.isEmpty()) {
                    null
                } else {
                    LockedModuleState(moduleName, rootPaths)
                }
            }
            .sortedBy { entry -> entry.moduleName }
            .forEach { entry ->
                add(sanitized.lockedModules, entry.moduleName, entry.rootPaths)
            }

        return sanitized
    }

    fun add(
        entries: MutableList<LockedModuleState>,
        moduleName: String,
        rootPaths: Collection<String>,
    ): Boolean {
        val normalizedModuleName = moduleName.trim()
        val normalizedRootPaths = rootPaths
            .asSequence()
            .map(::normalize)
            .filter { path -> path.isNotBlank() }
            .distinct()
            .sorted()
            .toList()
        if (normalizedModuleName.isBlank() || normalizedRootPaths.isEmpty()) {
            return false
        }

        val existing = entries.firstOrNull { entry -> entry.moduleName == normalizedModuleName }
        if (existing != null) {
            if (existing.rootPaths == normalizedRootPaths) {
                return false
            }
            existing.rootPaths = normalizedRootPaths.toMutableList()
            return true
        }

        entries.add(LockedModuleState(normalizedModuleName, normalizedRootPaths))
        entries.sortBy { entry -> entry.moduleName }
        return true
    }

    fun remove(entries: MutableList<LockedModuleState>, moduleName: String): Boolean {
        val normalizedModuleName = moduleName.trim()
        if (normalizedModuleName.isBlank()) {
            return false
        }
        return entries.removeAll { entry -> entry.moduleName == normalizedModuleName }
    }

    fun contains(entries: Collection<LockedModuleState>, moduleName: String): Boolean {
        val normalizedModuleName = moduleName.trim()
        if (normalizedModuleName.isBlank()) {
            return false
        }
        return entries.any { entry -> entry.moduleName == normalizedModuleName }
    }

    fun containsPath(entries: Collection<LockedModuleState>, path: String): Boolean {
        val normalizedPath = normalize(path)
        if (normalizedPath.isBlank()) {
            return false
        }
        return entries.any { entry ->
            entry.rootPaths.any { rootPath ->
                FileUtil.pathsEqual(rootPath, normalizedPath) ||
                    FileUtil.isAncestor(rootPath, normalizedPath, false)
            }
        }
    }

    fun normalize(path: String): String {
        val normalized = FileUtil.toSystemIndependentName(path.trim())
        return if (normalized.length > 1) normalized.trimEnd('/') else normalized
    }
}

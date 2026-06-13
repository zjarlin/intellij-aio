package site.addzero.cargo.buddy.model

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

object CargoCrateResolver {
    private const val CARGO_MANIFEST = "Cargo.toml"

    fun resolveCurrentCrate(project: Project): CargoCrate? {
        val selectedFile = FileEditorManager.getInstance(project).selectedEditor?.file ?: return null
        return resolveForFile(project, selectedFile)
    }

    fun resolveForFile(
        project: Project,
        file: VirtualFile,
    ): CargoCrate? {
        if (!file.isValid) return null
        val basePath = project.basePath ?: return null
        if (!file.path.startsWith(basePath)) return null

        val startDirectory = if (file.isDirectory) file else file.parent
        var directory = startDirectory
        while (directory != null && directory.path.startsWith(basePath)) {
            val manifest = directory.findChild(CARGO_MANIFEST)
            if (manifest != null && manifest.isValid && !manifest.isDirectory) {
                return CargoCrate.fromManifest(project, manifest)
            }
            directory = directory.parent
        }
        return null
    }

    fun isCargoFile(project: Project, file: VirtualFile): Boolean {
        if (!file.isValid || file.isDirectory) return false
        return resolveForFile(project, file) != null
    }
}


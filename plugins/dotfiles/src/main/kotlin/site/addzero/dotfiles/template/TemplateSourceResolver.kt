package site.addzero.dotfiles.template

import com.intellij.openapi.project.Project
import site.addzero.dotfiles.model.TemplateSourceType
import site.addzero.dotfiles.model.TemplateSpec
import site.addzero.dotfiles.repo.DotfilesLayout
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class TemplateSourceResolver {
    fun resolveText(project: Project, spec: TemplateSpec): String {
        return when (spec.sourceType) {
            TemplateSourceType.LOCAL -> readLocal(project, spec)
            TemplateSourceType.HTTP,
            TemplateSourceType.GIT -> readCached(project, spec)
        }
    }

    private fun readLocal(project: Project, spec: TemplateSpec): String {
        val dir = DotfilesLayout.templatesDir(project)
            ?: error("Project base path is not available.")
        val path = dir.resolve(spec.file)
        return readUtf8(path)
    }

    private fun readCached(project: Project, spec: TemplateSpec): String {
        val cacheDir = DotfilesLayout.cacheDir(project)
            ?: error("Project base path is not available.")
        val cacheFile = cacheDir.resolve(buildCacheFileName(spec))
        if (!Files.exists(cacheFile)) {
            error("Remote template not cached: ${spec.id}.")
        }
        return readUtf8(cacheFile)
    }

    private fun buildCacheFileName(spec: TemplateSpec): String {
        val safeId = spec.id.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return "$safeId.kts"
    }

    private fun readUtf8(path: Path): String =
        String(Files.readAllBytes(path), StandardCharsets.UTF_8)
}

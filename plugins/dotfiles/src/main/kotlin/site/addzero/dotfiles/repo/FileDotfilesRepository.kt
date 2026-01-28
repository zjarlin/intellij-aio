package site.addzero.dotfiles.repo

import com.intellij.openapi.project.Project
import site.addzero.dotfiles.model.DotfilesSpec
import java.nio.file.Files

class FileDotfilesRepository(
    private val format: DotfilesFormat = TomlDotfilesFormat(),
) : DotfilesRepository {

    override fun load(project: Project): DotfilesSpec {
        val path = DotfilesLayout.specPath(project) ?: return DotfilesSpec()
        if (!Files.exists(path)) return DotfilesSpec()
        return format.read(path)
    }

    override fun save(project: Project, spec: DotfilesSpec) {
        val path = DotfilesLayout.specPath(project) ?: return
        Files.createDirectories(path.parent)
        format.write(path, spec)
    }
}

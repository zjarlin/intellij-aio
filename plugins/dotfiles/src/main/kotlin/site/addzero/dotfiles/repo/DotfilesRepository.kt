package site.addzero.dotfiles.repo

import com.intellij.openapi.project.Project
import site.addzero.dotfiles.model.DotfilesSpec

interface DotfilesRepository {
    fun load(project: Project): DotfilesSpec

    fun save(project: Project, spec: DotfilesSpec)
}

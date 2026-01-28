package site.addzero.dotfiles.repo

import com.intellij.openapi.project.Project
import java.nio.file.Path
import java.nio.file.Paths

object DotfilesLayout {
    const val dirName = ".dotfiles"
    const val specFileName = "dotfiles.toml"
    const val templatesDirName = "templates"
    const val cacheDirName = ".cache"

    fun basePath(project: Project): Path? = project.basePath?.let(Paths::get)

    fun rootDir(project: Project): Path? = basePath(project)?.resolve(dirName)

    fun specPath(project: Project): Path? = rootDir(project)?.resolve(specFileName)

    fun templatesDir(project: Project): Path? = rootDir(project)?.resolve(templatesDirName)

    fun cacheDir(project: Project): Path? = rootDir(project)?.resolve(cacheDirName)
}

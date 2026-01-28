package site.addzero.dotfiles.repo

import site.addzero.dotfiles.model.DotfilesSpec
import java.nio.file.Path

interface DotfilesFormat {
    val fileName: String

    fun read(path: Path): DotfilesSpec

    fun write(path: Path, spec: DotfilesSpec)
}

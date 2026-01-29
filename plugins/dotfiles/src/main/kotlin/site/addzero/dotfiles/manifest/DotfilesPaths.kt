package site.addzero.dotfiles.manifest

import java.nio.file.Path
import java.nio.file.Paths

object DotfilesPaths {
    const val dirName = ".dotfiles"
    const val manifestFileName = "manifest.json"

    fun userHome(): Path = Paths.get(System.getProperty("user.home"))

    fun userDotfilesDir(): Path = userHome().resolve(dirName)

    fun userManifestPath(): Path = userDotfilesDir().resolve(manifestFileName)
}

package site.addzero.openprojecteverywhere.settings

import java.io.File

object OpenProjectEverywhereDefaults {
    const val DEFAULT_CLONE_ROOT = "~/IdeaProjects"

    fun defaultLocalProjectsRoot(): String {
        return File(System.getProperty("user.home"), "IdeaProjects").absolutePath
    }

    fun defaultCloneRoot(): String {
        return DEFAULT_CLONE_ROOT
    }

    fun expandHomeAwarePath(path: String): String {
        val home = System.getProperty("user.home").orEmpty()
        return when {
            path == "~" -> home
            path.startsWith("~/") -> home + path.removePrefix("~")
            path == "${'$'}HOME" -> home
            path.startsWith("${'$'}HOME/") -> home + "/" + path.removePrefix("${'$'}HOME/")
            path == "\${HOME}" -> home
            path.startsWith("\${HOME}/") -> home + "/" + path.removePrefix("\${HOME}/")
            else -> path
        }
    }
}

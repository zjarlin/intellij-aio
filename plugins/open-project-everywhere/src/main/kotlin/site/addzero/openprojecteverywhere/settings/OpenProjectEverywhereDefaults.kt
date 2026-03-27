package site.addzero.openprojecteverywhere.settings

import java.io.File

object OpenProjectEverywhereDefaults {
    fun defaultLocalProjectsRoot(): String {
        return File(System.getProperty("user.home"), "IdeaProjects").absolutePath
    }
}

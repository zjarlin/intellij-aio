package site.addzero.gradle.buddy.util

object GradleCommandLineUtil {

    private const val PUBLISH_TO_MAVEN_CENTRAL = "publishToMavenCentral"

    fun publishToMavenCentralCommand(modulePath: String): String {
        return "./gradlew ${publishToMavenCentralTaskPath(modulePath)}"
    }

    fun publishToMavenCentralTaskPath(modulePath: String): String {
        return taskPath(modulePath, PUBLISH_TO_MAVEN_CENTRAL)
    }

    private fun taskPath(modulePath: String, taskName: String): String {
        return when {
            modulePath.isBlank() || modulePath == ":" -> taskName
            else -> "$modulePath:$taskName"
        }
    }
}

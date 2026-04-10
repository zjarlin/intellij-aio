package site.addzero.gradle.buddy.util

object GradleCommandLineUtil {

    private const val PUBLISH_TO_MAVEN_CENTRAL = "publishToMavenCentral"
    private const val PUBLISH_ALL_PUBLICATIONS_TO_MAVEN_CENTRAL_REPOSITORY =
        "publishAllPublicationsToMavenCentralRepository"

    fun publishToMavenCentralCommand(modulePath: String): String {
        return "./gradlew ${publishToMavenCentralTaskPath(modulePath)}"
    }

    fun publishToMavenCentralTaskPath(modulePath: String): String {
        return taskPath(modulePath, PUBLISH_TO_MAVEN_CENTRAL)
    }

    fun publishSingleArtifactToMavenCentralCommand(modulePath: String): String {
        return "./gradlew ${publishSingleArtifactToMavenCentralTaskPath(modulePath)}"
    }

    fun publishSingleArtifactToMavenCentralTaskPath(modulePath: String): String {
        return taskPath(modulePath, PUBLISH_ALL_PUBLICATIONS_TO_MAVEN_CENTRAL_REPOSITORY)
    }

    private fun taskPath(modulePath: String, taskName: String): String {
        return when {
            modulePath.isBlank() || modulePath == ":" -> taskName
            else -> "$modulePath:$taskName"
        }
    }
}

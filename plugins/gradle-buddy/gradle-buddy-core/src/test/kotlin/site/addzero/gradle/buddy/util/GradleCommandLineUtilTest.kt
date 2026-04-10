package site.addzero.gradle.buddy.util

import kotlin.test.Test
import kotlin.test.assertEquals

class GradleCommandLineUtilTest {

    @Test
    fun `root single artifact publish uses repository task`() {
        assertEquals(
            "publishAllPublicationsToMavenCentralRepository",
            GradleCommandLineUtil.publishSingleArtifactToMavenCentralTaskPath(":"),
        )
        assertEquals(
            "./gradlew publishAllPublicationsToMavenCentralRepository",
            GradleCommandLineUtil.publishSingleArtifactToMavenCentralCommand(":"),
        )
    }

    @Test
    fun `module single artifact publish prefixes module path`() {
        assertEquals(
            ":lib:tool-kmp:tool-tree:publishAllPublicationsToMavenCentralRepository",
            GradleCommandLineUtil.publishSingleArtifactToMavenCentralTaskPath(":lib:tool-kmp:tool-tree"),
        )
        assertEquals(
            "./gradlew :lib:tool-kmp:tool-tree:publishAllPublicationsToMavenCentralRepository",
            GradleCommandLineUtil.publishSingleArtifactToMavenCentralCommand(":lib:tool-kmp:tool-tree"),
        )
    }
}

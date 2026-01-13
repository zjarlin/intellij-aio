package site.addzero.split.services

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/**
 * 构建系统接口
 */
interface BuildSystem {
    val buildFileName: String

    fun copyAndAdjustBuildFile(sourceModule: VirtualFile, newModuleDir: File, newModuleName: String)
    fun addDependency(sourceModule: VirtualFile, projectRoot: File, newModuleDir: File)

    companion object {
        fun detect(sourceModule: VirtualFile): BuildSystem {
            return when {
                sourceModule.findChild("build.gradle.kts") != null -> GradleKotlinBuildSystem()
                sourceModule.findChild("build.gradle") != null -> GradleGroovyBuildSystem()
                sourceModule.findChild("pom.xml") != null -> MavenBuildSystem()
                else -> throw IllegalStateException("Could not detect build system in ${sourceModule.name}")
            }
        }
    }
}

/**
 * Gradle Kotlin DSL (build.gradle.kts)
 */
class GradleKotlinBuildSystem : BuildSystem {
    override val buildFileName: String = "build.gradle.kts"

    override fun copyAndAdjustBuildFile(sourceModule: VirtualFile, newModuleDir: File, newModuleName: String) {
        val buildFile = sourceModule.findChild(buildFileName)!!
        val targetBuildFile = File(newModuleDir, buildFileName)
        buildFile.inputStream.use { input ->
            targetBuildFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    override fun addDependency(sourceModule: VirtualFile, projectRoot: File, newModuleDir: File) {
        val buildFile = sourceModule.findChild(buildFileName)!!
        val gradlePath = PathCalculator.calculateGradlePath(projectRoot, newModuleDir)
        val dependencyStatement = "    implementation(project(\"$gradlePath\"))"

        var content = String(buildFile.contentsToByteArray())
        if (content.contains("project(\"$gradlePath\")")) return

        val dependenciesBlockRegex = Regex("dependencies\\s*\\{([^}]*?)\\}", RegexOption.DOT_MATCHES_ALL)
        val match = dependenciesBlockRegex.find(content)

        if (match != null) {
            val blockContent = match.groupValues[1]
            val newBlockContent = blockContent + "\n$dependencyStatement"
            content = content.replace(match.value, "dependencies {$newBlockContent\n}")
        } else {
            content += "\n\ndependencies {\n$dependencyStatement\n}\n"
        }
        buildFile.setBinaryContent(content.toByteArray())
    }
}

/**
 * Gradle Groovy DSL (build.gradle)
 */
class GradleGroovyBuildSystem : BuildSystem {
    override val buildFileName: String = "build.gradle"

    override fun copyAndAdjustBuildFile(sourceModule: VirtualFile, newModuleDir: File, newModuleName: String) {
        val buildFile = sourceModule.findChild(buildFileName)!!
        val targetBuildFile = File(newModuleDir, buildFileName)
        buildFile.inputStream.use { input ->
            targetBuildFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    override fun addDependency(sourceModule: VirtualFile, projectRoot: File, newModuleDir: File) {
        val buildFile = sourceModule.findChild(buildFileName)!!
        val gradlePath = PathCalculator.calculateGradlePath(projectRoot, newModuleDir)
        val dependencyStatement = "    implementation project('$gradlePath')"

        var content = String(buildFile.contentsToByteArray())
        if (content.contains("project('$gradlePath')") || content.contains("project(\"$gradlePath\")")) return

        val dependenciesBlockRegex = Regex("dependencies\\s*\\{([^}]*?)\\}", RegexOption.DOT_MATCHES_ALL)
        val match = dependenciesBlockRegex.find(content)

        if (match != null) {
            val blockContent = match.groupValues[1]
            val newBlockContent = blockContent + "\n$dependencyStatement"
            content = content.replace(match.value, "dependencies {$newBlockContent\n}")
        } else {
            content += "\n\ndependencies {\n$dependencyStatement\n}\n"
        }
        buildFile.setBinaryContent(content.toByteArray())
    }
}

/**
 * Maven (pom.xml)
 */
class MavenBuildSystem : BuildSystem {
    override val buildFileName: String = "pom.xml"

    override fun copyAndAdjustBuildFile(sourceModule: VirtualFile, newModuleDir: File, newModuleName: String) {
        val pomFile = sourceModule.findChild(buildFileName)!!
        var content = String(pomFile.contentsToByteArray())

        // Update artifactId to new module name
        content = content.replace(Regex("<artifactId>.*?</artifactId>"), "<artifactId>$newModuleName</artifactId>")

        val targetPomFile = File(newModuleDir, buildFileName)
        targetPomFile.writeText(content)
    }

    override fun addDependency(sourceModule: VirtualFile, projectRoot: File, newModuleDir: File) {
        val pomFile = sourceModule.findChild(buildFileName)!!
        var content = String(pomFile.contentsToByteArray())

        // Extract groupId and version from source pom
        val groupIdMatch = Regex("<groupId>(.*?)</groupId>").find(content)
        val versionMatch = Regex("<version>(.*?)</version>").find(content)
        val groupId = groupIdMatch?.groupValues?.get(1) ?: "unknown"
        val version = versionMatch?.groupValues?.get(1) ?: "1.0-SNAPSHOT"
        val artifactId = newModuleDir.name

        val dependencyStatement = """
        <dependency>
            <groupId>$groupId</groupId>
            <artifactId>$artifactId</artifactId>
            <version>$version</version>
        </dependency>"""

        if (content.contains("<artifactId>$artifactId</artifactId>")) return

        val dependenciesBlockRegex = Regex("<dependencies>\\s*(.*?)\\s*</dependencies>", RegexOption.DOT_MATCHES_ALL)
        val match = dependenciesBlockRegex.find(content)

        if (match != null) {
            val blockContent = match.groupValues[1]
            val newBlockContent = blockContent + "\n$dependencyStatement"
            content = content.replace(match.value, "<dependencies>\n$newBlockContent\n    </dependencies>")
        } else {
            // Find project closing tag
            content = content.replace("</project>", "    <dependencies>\n$dependencyStatement\n    </dependencies>\n</project>")
        }
        pomFile.setBinaryContent(content.toByteArray())
    }
}

package site.addzero.maven.search.detect

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import site.addzero.maven.search.DependencyFormat

/**
 * 项目构建类型检测器
 * 
 * 自动检测项目是 Maven / Gradle Kotlin DSL / Gradle Groovy DSL
 */
object ProjectBuildTypeDetector {

    /**
     * 检测项目的构建类型并返回对应的依赖格式
     * 
     * 优先级：
     * 1. Gradle Kotlin DSL (build.gradle.kts / settings.gradle.kts)
     * 2. Gradle Groovy DSL (build.gradle / settings.gradle)
     * 3. Maven (pom.xml)
     * 4. 默认: Gradle Kotlin DSL
     */
    fun detect(project: Project): DependencyFormat {
        val baseDir = project.baseDir ?: return DependencyFormat.GRADLE_KOTLIN
        
        return when {
            hasGradleKotlin(baseDir) -> DependencyFormat.GRADLE_KOTLIN
            hasGradleGroovy(baseDir) -> DependencyFormat.GRADLE_GROOVY
            hasMaven(baseDir) -> DependencyFormat.MAVEN
            else -> DependencyFormat.GRADLE_KOTLIN
        }
    }

    private fun hasGradleKotlin(baseDir: VirtualFile): Boolean =
        baseDir.findChild("build.gradle.kts") != null ||
        baseDir.findChild("settings.gradle.kts") != null

    private fun hasGradleGroovy(baseDir: VirtualFile): Boolean =
        (baseDir.findChild("build.gradle") != null ||
         baseDir.findChild("settings.gradle") != null) &&
        !hasGradleKotlin(baseDir)

    private fun hasMaven(baseDir: VirtualFile): Boolean =
        baseDir.findChild("pom.xml") != null
}

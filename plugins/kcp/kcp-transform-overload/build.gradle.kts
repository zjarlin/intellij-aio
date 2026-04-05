import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask

plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}
val libs = versionCatalogs.named("libs")

dependencies {
    implementation(project(":lib:kcp:transform-overload:kcp-transform-overload-annotations"))
    testImplementation(libs.findLibrary("junit-junit").get())
}

tasks.test {
  useJUnit()
}

tasks.named<RunIdeTask>("runIde") {
    dependsOn(
        ":lib:kcp:transform-overload:kcp-transform-overload-annotations:publishToMavenLocal",
        ":lib:kcp:transform-overload:kcp-transform-overload-plugin:publishToMavenLocal",
        ":lib:kcp:transform-overload:kcp-transform-overload-gradle-plugin:publishToMavenLocal",
    )
    args(
        project.rootDir
            .toPath()
            .resolve("example-transform-overload")
            .normalize()
            .toFile()
            .absolutePath,
    )
}

import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask

plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}

group = "site.addzero"
val libs = versionCatalogs.named("libs")

dependencies {
    implementation(project(":lib:kcp:spread-pack:kcp-spread-pack-annotations"))
    testImplementation(libs.findLibrary("junit-junit").get())
}

tasks.test {
    useJUnit()
}

tasks.named<RunIdeTask>("runIde") {
    dependsOn(
        ":lib:kcp:spread-pack:kcp-spread-pack-annotations:publishToMavenLocal",
        ":lib:kcp:spread-pack:kcp-spread-pack-plugin:publishToMavenLocal",
        ":lib:kcp:spread-pack:kcp-spread-pack-gradle-plugin:publishToMavenLocal",
    )
    args(
        project.rootDir
            .toPath()
            .resolve("example/example-spread-pack")
            .normalize()
            .toFile()
            .absolutePath,
    )
}

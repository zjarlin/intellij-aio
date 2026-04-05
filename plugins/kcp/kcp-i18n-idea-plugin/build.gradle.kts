import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask

plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}

group = "site.addzero"

tasks.named<RunIdeTask>("runIde") {
    dependsOn(":lib:kcp:kcp-i18n:jar")
    args(project.rootDir.absolutePath)
}

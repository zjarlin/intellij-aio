plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}
val libs = versionCatalogs.named("libs")

group = "site.addzero"

dependencies {
    implementation(project(":lib:kcp:multireceiver:kcp-multireceiver-annotations"))
    testImplementation(libs.findLibrary("junit-junit").get())
    testImplementation(libs.findLibrary("org-junit-jupiter-junit-jupiter").get())
}

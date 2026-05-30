plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}
val libs = versionCatalogs.named("libs")

dependencies {
    implementation(libs.findLibrary("site-addzero-tool-api-maven").get())

    implementation(project(":plugins:maven-buddy:maven-buddy-core"))
}


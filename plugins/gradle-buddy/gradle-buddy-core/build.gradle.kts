plugins {
    id("site.addzero.buildlogic.intellij.intellij-core")
}
val libs = versionCatalogs.named("libs")

dependencies {
    intellijPlatform {
        bundledPlugin("org.jetbrains.plugins.gradle")
    }
    implementation(libs.findLibrary("site-addzero-tool-api-maven").get())
}

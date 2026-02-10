plugins {
    id("site.addzero.buildlogic.intellij.intellij-core")
}

dependencies {
    intellijPlatform {
        bundledPlugin("org.jetbrains.plugins.gradle")
    }
    implementation(libs.site.addzero.tool.api.maven)
}

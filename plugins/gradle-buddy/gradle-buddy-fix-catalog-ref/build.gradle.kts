plugins {
    id("site.addzero.buildlogic.intellij.intellij-core")
}

dependencies {
    implementation(project(":plugins:gradle-buddy:gradle-buddy-core"))
    implementation(project(":lib:tool-psi-toml"))
    intellijPlatform {
        bundledPlugin("org.jetbrains.plugins.gradle")
        bundledPlugin("org.jetbrains.kotlin")
        bundledPlugin("org.toml.lang")
    }
}

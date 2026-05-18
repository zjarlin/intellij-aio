plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}

val libs = versionCatalogs.named("libs")

dependencies {
    testImplementation(libs.findLibrary("junit-junit").get())
}

tasks.test {
    useJUnit()
}

intellijPlatform {
    pluginConfiguration {
        id = "site.addzero.recent-project-cleaner"
        name = "Recent Project Cleaner"
    }
}

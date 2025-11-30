plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}

intellijPlatform {
    pluginConfiguration {
        id = "site.addzero.gradle-buddy"
        name = "Gradle Buddy"
        version = "2025.11.31"
        description = "A plugin that helps you manage Gradle projects by showing an indicator when a project is not loaded"
    }
}

dependencies {
    intellijPlatform {
        bundledPlugin("org.jetbrains.plugins.gradle")
    }
}
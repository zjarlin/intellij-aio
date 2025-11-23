plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}

intellijPlatform {
    pluginConfiguration {
        id = "site.addzero.gradle-favorites"
        name = "Gradle Favorites"
    }
}

dependencies {
    intellijPlatform {
        bundledPlugin("org.jetbrains.plugins.gradle")
    }
}

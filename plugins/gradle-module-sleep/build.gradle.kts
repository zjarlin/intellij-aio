plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}

val pluginName = "gradle-module-sleep"
intellijPlatform {
    pluginConfiguration {
        id = "site.addzero.$pluginName"
        name = "Gradle Module Sleep"
    }
}

dependencies {
    intellijPlatform {
        bundledPlugin("org.jetbrains.plugins.gradle")
        bundledPlugin("com.intellij.java")
    }
}

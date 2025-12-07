plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}

val pluginName = project.name
intellijPlatform {
    pluginConfiguration {
        id = "site.addzero.$pluginName"
        name = pluginName
    }
}

dependencies {
    intellijPlatform {
        bundledPlugin("org.jetbrains.plugins.gradle")
        bundledPlugin("com.intellij.java")
    }
    implementation(libs.tool.api.maven)
}

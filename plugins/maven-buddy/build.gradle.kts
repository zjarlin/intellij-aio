plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}
dependencies {
    implementation("site.addzero:tool-api-maven:2025.11.28")
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")
}

val pluginName = project.name
intellijPlatform {
    pluginConfiguration {
        id = "site.addzero.$pluginName"
        name = pluginName
        version = "2025.11.30"
    }
}

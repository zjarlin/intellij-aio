plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}
dependencies {
    implementation(libs.tool.api.maven)
    implementation(libs.sqlite.jdbc)
}

val pluginName = project.name
intellijPlatform {
    pluginConfiguration {
        id = "site.addzero.$pluginName"
        name = pluginName
    }
}

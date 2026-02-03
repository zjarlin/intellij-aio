plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}

dependencies {
    intellijPlatform {
        bundledPlugin("org.jetbrains.plugins.gradle")
    }
}
val pluginName = project.name
intellijPlatform {
  pluginConfiguration {
    id = "site.addzero.$pluginName"
    name = pluginName
    version = "2026.02.04"
  }
}

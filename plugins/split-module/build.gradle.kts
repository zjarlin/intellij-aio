plugins {
  id("site.addzero.buildlogic.intellij.intellij-platform")
}

val pluginName = project.name
intellijPlatform {
  pluginConfiguration {
    id = "site.addzero.$pluginName"
    name = pluginName
    version = "2026.01.14"
  }
}

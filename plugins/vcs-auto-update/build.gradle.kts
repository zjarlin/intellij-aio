plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}


dependencies {
    intellijPlatform {
        // Git4Idea for git push listener
        bundledPlugin("Git4Idea")
    }
}
val pluginName = project.name
intellijPlatform {
  pluginConfiguration {
    id = "site.addzero.$pluginName"
    name = pluginName
//    version = "2026.01.16"
  }
}

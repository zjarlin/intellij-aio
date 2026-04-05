plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}

val libs = versionCatalogs.named("libs")

dependencies {
    intellijPlatform {
        bundledPlugin("org.jetbrains.plugins.gradle")
    }

    testImplementation(libs.findLibrary("junit-junit").get())
}

tasks.test {
    useJUnit()
}

val pluginName = project.name
intellijPlatform {
  pluginConfiguration {
    id = "site.addzero.$pluginName"
    name = pluginName
//    version = "2026.02.05"
  }
}

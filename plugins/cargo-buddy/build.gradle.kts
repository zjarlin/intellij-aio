plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}
val libs = versionCatalogs.named("libs")

val pluginName = project.name
intellijPlatform {
    pluginConfiguration {
        id = "site.addzero.$pluginName"
        name = "Cargo Buddy"
    }
}

dependencies {
    implementation(libs.findLibrary("com-google-code-gson-gson").get())
    testImplementation(libs.findLibrary("junit-junit").get())

    val rustPluginPath = providers.gradleProperty("rust.plugin.path")
        .orElse(
            providers.provider {
                "/Users/zjarlin/Library/Application Support/JetBrains/RustRover2026.1/plugins/intellij-rust"
            },
        )
    val rustPluginDir = file(rustPluginPath.get())
    if (rustPluginDir.exists()) {
        intellijPlatform {
            localPlugin(rustPluginDir)
        }
    }
}

tasks.test {
    useJUnit()
}

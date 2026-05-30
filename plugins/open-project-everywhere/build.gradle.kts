plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}
val libs = versionCatalogs.named("libs")

dependencies {
    intellijPlatform {
        bundledPlugin("Git4Idea")
        bundledPlugin("org.jetbrains.plugins.github")
    }
    implementation(libs.findLibrary("com-google-code-gson-gson").get())
    implementation(libs.findLibrary("com-squareup-okhttp3-okhttp").get())
}

//intellijPlatform {
//    autoReload.set(false)

//    pluginConfiguration {
//        id = "site.addzero.open-project-everywhere"
//        name = "Open Project Everywhere"
//    }
//}

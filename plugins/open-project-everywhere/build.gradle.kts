plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}

dependencies {
    intellijPlatform {
        bundledPlugin("Git4Idea")
        bundledPlugin("org.jetbrains.plugins.github")
    }
    implementation(libs.com.google.code.gson.gson)
    implementation(libs.com.squareup.okhttp3.okhttp)
}

//intellijPlatform {
//    autoReload.set(false)

//    pluginConfiguration {
//        id = "site.addzero.open-project-everywhere"
//        name = "Open Project Everywhere"
//    }
//}

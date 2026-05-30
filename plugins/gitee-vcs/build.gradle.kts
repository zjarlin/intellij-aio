plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}
val libs = versionCatalogs.named("libs")

dependencies {
    intellijPlatform {
        bundledPlugin("Git4Idea")
    }
    implementation(libs.findLibrary("com-squareup-okhttp3-okhttp").get())
}

val pluginName = project.name
intellijPlatform {
    pluginConfiguration {
        id = "site.addzero.$pluginName"
//        name = "Gitee"
    }
}

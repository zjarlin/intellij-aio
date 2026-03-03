plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}

dependencies {
    intellijPlatform {
        bundledPlugin("Git4Idea")
    }
    implementation(libs.com.squareup.okhttp3.okhttp)
}

val pluginName = project.name
intellijPlatform {
    pluginConfiguration {
        id = "site.addzero.$pluginName"
//        name = "Gitee"
    }
}

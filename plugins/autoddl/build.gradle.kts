
plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}
val libs = versionCatalogs.named("libs")

intellijPlatform {
    pluginConfiguration {
        id = "com.addzero.autoddl"
        name = "AutoDDL"
    }
}

dependencies {

//    implementation(project(":checkouts:lsi:lsi-core"))

//    implementation(project(":checkouts:lsi:lsi-intellij"))
//    implementation(project(":checkouts:lsi:lsi-psi"))
//    implementation(project(":checkouts:lsi:lsi-kt"))
    implementation(project(":lib:tool-swing"))
    implementation(project(":lib:tool-awt"))
    implementation(project(":lib:tool-psi-toml"))
    implementation(project(":lib:ide-component-settings-old"))


    implementation(libs.findLibrary("site-addzero-tool-ai").get())
    implementation(libs.findLibrary("site-addzero-tool-toml").get())
    implementation(libs.findLibrary("site-addzero-tool-io-codegen").get())
    implementation(libs.findLibrary("site-addzero-tool-str").get())
    implementation(libs.findLibrary("site-addzero-tool-jvmstr").get())
    implementation(libs.findLibrary("site-addzero-tool-coll").get())
    implementation(libs.findLibrary("site-addzero-tool-reflection").get())

}
